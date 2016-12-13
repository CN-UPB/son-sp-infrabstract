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

    public void compute_vnf_load(List<String> overload_vnf_list_t, List<String> underload_vnf_list_t)
    {

        for (Map.Entry<String, Map<String, FunctionInstance>> finst_l : instance.function_list.entrySet()) {
            for (Map.Entry<String, FunctionInstance> finst_t : finst_l.getValue().entrySet()) {
                PerformanceThreshold p_thresh = threshold_m.get(finst_t.getValue().function.getVnfId());
                if(null == p_thresh) {
                    logger.error("ComputeMetrics::compute_vnf_load: No threshold levels found for vnf "
                            + finst_t.getValue().function.getVnfId());
                    continue;
                }

                List<MonitorStats> stats_history_t = stats_history.get(finst_t.getValue().getName());
                MonitorStats stats_t = stats_history_t != null ? stats_history_t.get(stats_history_t.size()-1) : null;

                if(stats_t == null)
                    continue;

                if(threshold_m.get(finst_t.getValue().function.getVnfId()).getCpu_upper_l() < stats_t.getCpu())
                {
                    logger.warn("ComputerMetrics::compute_vnf_load: cpu overload for " + finst_t.getKey()
                    + " upper threshold : " + threshold_m.get(finst_t.getValue().function.getVnfId()).getCpu_upper_l()
                            + " current cpu load : " + stats_t.getCpu());
                    overload_vnf_list_t.add(finst_t.getValue().function.getVnfId());

                } else if(threshold_m.get(finst_t.getValue().function.getVnfId()).getMem_upper_l() < stats_t.getMemoryUsed())
                {
                    logger.warn("ComputerMetrics::compute_vnf_load: memory overload for " + finst_t.getKey()
                            + " upper threshold : " + threshold_m.get(finst_t.getValue().function.getVnfId()).getMem_upper_l()
                            + " current memory usage : " + stats_t.getMemoryUsed());
                    overload_vnf_list_t.add(finst_t.getValue().function.getVnfId());
                } else if(threshold_m.get(finst_t.getValue().function.getVnfId()).getCpu_lower_l() > stats_t.getCpu())
                {
                    logger.warn("ComputerMetrics::compute_vnf_load: cpu underload for " + finst_t.getKey()
                            + " lower threshold : " + threshold_m.get(finst_t.getValue().function.getVnfId()).getCpu_lower_l()
                            + " current cpu load : " + stats_t.getCpu());
                    underload_vnf_list_t.add(finst_t.getValue().function.getVnfId());

                } else if(threshold_m.get(finst_t.getValue().function.getVnfId()).getMem_lower_l() > stats_t.getMemoryUsed())
                {
                    logger.warn("ComputerMetrics::compute_vnf_load: memory underload for " + finst_t.getKey()
                            + " lower threshold : " + threshold_m.get(finst_t.getValue().function.getVnfId()).getMem_lower_l()
                            + " current memory usage : " + stats_t.getMemoryUsed());
                    underload_vnf_list_t.add(finst_t.getValue().function.getVnfId());
                }

            }
        }

        return;
    }


}
