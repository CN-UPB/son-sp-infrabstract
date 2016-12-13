package sonata.kernel.placement.service;

import org.apache.log4j.Logger;
import sonata.kernel.placement.PlacementConfigLoader;
import sonata.kernel.placement.config.PerformanceThreshold;
import sonata.kernel.placement.config.PlacementConfig;
import sonata.kernel.placement.monitor.MonitorHistory;
import sonata.kernel.placement.monitor.MonitorStats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComputeMetrics {
    final static Logger logger = Logger.getLogger(ComputeMetrics.class);

    public final ServiceInstance instance;

    //Threshold levels Network-function <-> Threshold
    public final Map<String, List<MonitorStats>> stats_history;
    public final HashMap<String, PerformanceThreshold> threshold_m;

    public ComputeMetrics(ServiceInstance instance, Map<String, List<MonitorStats>> stats_history) {
        this.instance = instance;
        this.stats_history = stats_history;
        threshold_m = new HashMap<String, PerformanceThreshold>();
        initialize_threshold();
    }

    private void initialize_threshold()
    {
        //Read the threshold levels from the configuration.
        PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();
        List<PerformanceThreshold> threshold_ll = config.getThreshold();

        for(int i=0; i < threshold_ll.size(); i++)
            threshold_m.put(threshold_ll.get(i).getVnfId(), threshold_ll.get(0));

        return;

    }


    public void update_threshold(String vnf_id, PerformanceThreshold threshold)
    {
        if( null != threshold_m.get(vnf_id)) {
            threshold_m.remove(vnf_id);
            threshold_m.put(vnf_id, threshold);
        } else {
            threshold_m.put(vnf_id, threshold);
        }

        return;
    }

    protected void evaluate_vnf_load(Map<String, List<String>> m_overload,
                                     Map<String, List<String>> m_underload,
                                     List<String> overload_vnf_list,
                                     List<String> underload_vnf_list)
    {
        for(Map.Entry<String, List<String>> entry : m_overload.entrySet())
        {
            if(instance.function_list.get(entry.getKey()).size() * (threshold_m.get(entry.getKey()).getScale_out_upper_l())/100
                    < entry.getValue().size())
            {
                logger.info("ComputeMetrics::evaluate_vnf_load: " + (entry.getValue().size()/instance.function_list.get(entry.getKey()).size())/100
                        + " vnfs of type " + entry.getKey() + " overloaded ");
                overload_vnf_list.add(entry.getKey());
            }
        }

        for(Map.Entry<String, List<String>> entry : m_underload.entrySet())
        {
            if(instance.function_list.get(entry.getKey()).size() * (threshold_m.get(entry.getKey()).getScale_in_lower_l())/100
                    < entry.getValue().size())
            {
                logger.info("ComputeMetrics::evaluate_vnf_load: " + (entry.getValue().size()/instance.function_list.get(entry.getKey()).size())/100
                                        + " vnfs of type " + entry.getKey() + " underloaded ");
                underload_vnf_list.add(entry.getKey());
            }
        }

        return;
    }

    protected void evaluate_vnf_instance_load(FunctionInstance f_inst,
                                 List<String> overload_vnf_list_t,
                                 List<String> underload_vnf_list_t)
    {
        List<MonitorStats> stats_history_t = stats_history.get(f_inst.name);

        if(stats_history_t == null) {
            logger.warn("ComputeMetrics::compute_vnf_load: Not monitoring stats available for VNF: " + f_inst.name);
            return;
        }

        if(stats_history_t.size() < threshold_m.get(f_inst.function.getVnfId()).getHistory_check())
        {
            logger.warn("ComputeMetrics::compute_vnf_load: Not enough monitoring stats available for VNF: " + f_inst.name);
            return;
        }

        for (int i=0; i < threshold_m.get(f_inst.function.getVnfId()).getHistory_check(); i++) {

            MonitorStats stats_t = stats_history_t.get(i);
            
            if (((threshold_m.get(f_inst.function.getVnfId()).getCpu_upper_l()) / 100) * stats_t.getCpuCores() < stats_t.getCpu()) {
                logger.error("ComputerMetrics::compute_vnf_load: cpu overload for " + f_inst.name
                        + " upper threshold : " + threshold_m.get(f_inst.function.getVnfId()).getCpu_upper_l()
                        + " current cpu load : " + stats_t.getCpu());
                overload_vnf_list_t.add(f_inst.name);

            } else if (threshold_m.get(f_inst.function.getVnfId()).getMem_upper_l() < stats_t.getMemoryPercentage()) {
                logger.error("ComputerMetrics::compute_vnf_load: memory overload for " + f_inst.name
                        + " upper threshold : " + threshold_m.get(f_inst.function.getVnfId()).getMem_upper_l()
                        + " current memory usage : " + stats_t.getMemoryPercentage());
                overload_vnf_list_t.add(f_inst.name);
            } else if (((threshold_m.get(f_inst.function.getVnfId()).getCpu_lower_l()) / 100) * stats_t.getCpuCores() > stats_t.getCpu()) {
                logger.error("ComputerMetrics::compute_vnf_load: cpu underload for " + f_inst.name
                        + " lower threshold : " + threshold_m.get(f_inst.function.getVnfId()).getCpu_lower_l()
                        + " current cpu load : " + stats_t.getCpu());
                underload_vnf_list_t.add(f_inst.name);

            } else if (threshold_m.get(f_inst.function.getVnfId()).getMem_lower_l() > stats_t.getMemoryPercentage()) {
                logger.error("ComputerMetrics::compute_vnf_load: memory underload for " + f_inst.name
                        + " lower threshold : " + threshold_m.get(f_inst.function.getVnfId()).getMem_lower_l()
                        + " current memory usage : " + stats_t.getMemoryPercentage());
                underload_vnf_list_t.add(f_inst.name);
            }
        }
    }

    public void compute_vnf_load(List<String> overload_vnf_list_t, List<String> underload_vnf_list_t)
    {
        Map<String, List<String>> m_overload = new HashMap<String, List<String>>();
        Map<String, List<String>> m_underload = new HashMap<String, List<String>>();

        for (Map.Entry<String, Map<String, FunctionInstance>> finst_l : instance.function_list.entrySet()) {

            List<String> overload_l = new ArrayList<String>();
            List<String> underload_l = new ArrayList<String>();

            for (Map.Entry<String, FunctionInstance> finst_t : finst_l.getValue().entrySet()) {
                PerformanceThreshold p_thresh = threshold_m.get(finst_t.getValue().function.getVnfId());
                if(null == p_thresh) {
                    logger.error("ComputeMetrics::compute_vnf_load: No threshold levels found for vnf "
                            + finst_t.getValue().function.getVnfId());
                    continue;
                }

                evaluate_vnf_instance_load(finst_t.getValue(), overload_l, underload_l);

            }
            m_overload.put(finst_l.getKey(), overload_l);
            m_underload.put(finst_l.getKey(), underload_l);

            evaluate_vnf_load(m_overload, m_underload,
                    overload_vnf_list_t, underload_vnf_list_t);
        }
        return;
    }


}
