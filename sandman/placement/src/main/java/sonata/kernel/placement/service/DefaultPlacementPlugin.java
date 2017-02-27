package sonata.kernel.placement.service;

import sonata.kernel.VimAdaptor.commons.DeployServiceData;
import sonata.kernel.placement.DatacenterManager;
import sonata.kernel.placement.config.PopResource;


import java.util.*;

import org.apache.log4j.Logger;

public class DefaultPlacementPlugin implements PlacementPlugin {
    final static Logger logger = Logger.getLogger(DefaultPlacementPlugin.class);

    ServiceInstanceManager instance_manager;

    @Override
    public ServiceInstance initialScaling(DeployServiceData serviceData) {
        logger.info("Initial Scaling");



        PlacementManager pm = new PlacementManager();
        ServiceInstance inst = pm.InitializeService(serviceData);

        /*
        instance_manager = new ServiceInstanceManager();
        ServiceInstance inst =  instance_manager.initialize_service_instance(serviceData);
        */
        return inst;

    }



    @Override
    public ServiceInstance updateScaling(DeployServiceData serviceData, ServiceInstance instance, MonitorMessage message) {
        logger.info("Update Scaling");


        PlacementManager pm = new PlacementManager(instance);
        //SAMPLE 1

        /*
        instance_manager = new ServiceInstanceManager();
        instance_manager.set_instance(instance);
        instance_manager.flush_chaining_rules();
        */

        List<String> overload_l = new ArrayList<String>();
        List<String> underload_l = new ArrayList<String>();

        ComputeMetrics c_metrics = new ComputeMetrics(instance, message.stats_history);
        c_metrics.compute_vnf_load(overload_l, underload_l);

        /*
        if(overload_l.size() > 0)
        {
            instance_manager.update_functions_list(overload_l.get(0).split("_")[1], null, ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);
        }

        //Reason to scale-in
        if(underload_l.size() < 0)
        {

        }
        */

        //

        /*
        Sample Scale-out scheme
        Add an additiontional tcpdump vnf.
        Add a loadbalancer vnf between the firewall vnf and the tcpdump vnfs

                                                           ___________ tcpdump1
                 __________            ___________        |
                |          |          |           ||------|
        - - ---||  FW1     ||--------||   LB1     |
                |__________|          |___________||------|
                                                          |___________ tcpdump2


         */
        if (message.type == MonitorMessage.SCALE_TYPE.SCALE_OUT) {
            //instance_manager.update_functions_list("vnf_loadbalancer", null, ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);
            ///instance_manager.update_functions_list("vnf_tcpdump", null, "datacenter3", ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            pm.AddNetworkFunctionInstance("vnf_tcpdump", "dc1");

            ///instance_manager.update_vlink_list("vnf_firewall", "vnf_tcpdump", "vnf_firewall1", "vnf_tcpdump1", null,
            ///     ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);

            pm.DeleteVirtualLink("firewall1", "tcpdump1");
            /*
            instance_manager.update_vlink_list("vnf_firewall", "vnf_loadbalancer", "vnf_firewall1", "vnf_loadbalancer1",
                    ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            instance_manager.update_vlink_list("vnf_loadbalancer", "vnf_tcpdump", "vnf_loadbalancer1", "vnf_tcpdump1",
                    ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            instance_manager.update_vlink_list("vnf_loadbalancer", "vnf_tcpdump", "vnf_loadbalancer1", "vnf_tcpdump2",
                    ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            instance_manager.update_functions_list("vnf_tcpdump", null, ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            ServiceInstance instance_t =  instance_manager.update_vlink_list("vnf_loadbalancer", "vnf_tcpdump", "vnf_loadbalancer1", "vnf_tcpdump3",
                    ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);*/

            ///instance_manager.update_vlink_list("vnf_firewall", "vnf_tcpdump", "vnf_firewall1", "vnf_tcpdump1", null,
            ///     ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            pm.AddVirtualLink("firewall1", "tcpdump1", null);

            ///instance_manager.update_vlink_list("vnf_firewall", "vnf_tcpdump", "vnf_firewall1", "vnf_tcpdump2", null,
            ///     ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            pm.AddVirtualLink("firewall1", "tcpdump2", null);

            ///instance_manager.update_functions_list("vnf_tcpdump", null, "datacenter3", ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            pm.AddNetworkFunctionInstance("vnf_tcpdump", "dc2");

            /*List<String> s1 = new ArrayList<String>();
            s1.add("s1");
            s1.add("s2");*/
            ///ServiceInstance instance_t =  instance_manager.update_vlink_list("vnf_firewall", "vnf_tcpdump", "vnf_firewall1", "vnf_tcpdump3", s1,
            ///     ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            pm.AddVirtualLink("firewall1", "tcpdump3", null);

            pm.AddNetworkFunctionInstance("vnf_iperf", "dc1");
            pm.AddVirtualLink("iperf2", "firewall1", null);

            ServiceInstance instance_t = pm.GetServiceInstance();

            ServiceGraph graph = new ServiceGraph(instance);
            Node node = graph.generate_graph();

            return instance_t;



        } else if (message.type == MonitorMessage.SCALE_TYPE.SCALE_IN) {

            /*instance_manager.update_vlink_list("vnf_loadbalancer", "vnf_tcpdump", "vnf_loadbalancer1", "vnf_tcpdump2",
                    ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);
            instance_manager.update_functions_list("vnf_tcpdump", "vnf_tcpdump2", ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);
*/
            ///instance_manager.update_vlink_list("vnf_firewall", "vnf_tcpdump", "vnf_firewall1", "vnf_tcpdump1", null,
            ///     ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);

            //pm.DeleteVirtualLink("vnf_firewall1", "vnf_tcpdump1");

            ///instance_manager.update_functions_list("vnf_tcpdump", "vnf_tcpdump1", "datacenter1", ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);

            pm.MoveNetworkFunctionInstance("tcpdump3", "dc1");
            pm.DeleteNetworkFunctionInstance("firewall1");
            //pm.DeleteNetworkFunctionInstance("tcpdump3");

            return pm.GetServiceInstance();
        }
        return null;
    }

}