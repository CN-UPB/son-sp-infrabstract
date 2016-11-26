package sonata.kernel.placement.service;

import sonata.kernel.VimAdaptor.commons.DeployServiceData;
import sonata.kernel.placement.config.NodeResource;
import sonata.kernel.placement.config.PopResource;


import java.util.*;

import org.apache.log4j.Logger;

public class DefaultPlacementPlugin implements PlacementPlugin {
    final static Logger logger = Logger.getLogger(DefaultPlacementPlugin.class);

    ServiceInstanceManager instance_manager;

    @Override
    public ServiceInstance initialScaling(DeployServiceData serviceData) {
        logger.info("Initial Scaling");

        instance_manager = new ServiceInstanceManager();
        return instance_manager.initialize_service_instance(serviceData);

    }



    @Override
    public ServiceInstance updateScaling(DeployServiceData serviceData, ServiceInstance instance, MonitorMessage trigger) {
        logger.info("Update Scaling");

        //SAMPLE 1
        instance_manager.set_instance(instance);
        instance_manager.flush_chaining_rules();
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
        if (trigger.type == MonitorMessage.SCALE_TYPE.SCALE_OUT) {
            instance_manager.update_functions_list("vnf_loadbalancer", null, ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);
            instance_manager.update_functions_list("vnf_tcpdump", null, ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            instance_manager.update_vlink_list("vnf_firewall", "vnf_tcpdump", "vnf_firewall1", "vnf_tcpdump1",
                    ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);

            instance_manager.update_vlink_list("vnf_firewall", "vnf_loadbalancer", "vnf_firewall1", "vnf_loadbalancer1",
                    ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            instance_manager.update_vlink_list("vnf_loadbalancer", "vnf_tcpdump", "vnf_loadbalancer1", "vnf_tcpdump1",
                    ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            instance_manager.update_vlink_list("vnf_loadbalancer", "vnf_tcpdump", "vnf_loadbalancer1", "vnf_tcpdump2",
                    ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

            instance_manager.update_functions_list("vnf_tcpdump", null, ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);
            return instance_manager.update_vlink_list("vnf_loadbalancer", "vnf_tcpdump", "vnf_loadbalancer1", "vnf_tcpdump3",
                    ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);



        } else if (trigger.type == MonitorMessage.SCALE_TYPE.SCALE_IN) {
            instance_manager.update_vlink_list("vnf_loadbalancer", "vnf_tcpdump", "vnf_loadbalancer1", "vnf_tcpdump1",
                    ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);

            return instance_manager.update_functions_list("vnf_tcpdump", "vnf_tcpdump1", ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);
        }
        return null;
    }

    @Override
    public PlacementMapping initialPlacement(DeployServiceData serviceData, ServiceInstance instance, List<PopResource> resources) {
        logger.info("Initial Placement");
        PlacementMapping mapping = new PlacementMapping();
        mapping.resources.addAll(resources);

        // For every pop there is a list of available nodes
        List<List<String>> availableResourceNodes = new ArrayList<List<String>>();
        int availableNodeCounter = 0;

        // TODO: actually need uniquely identifiable resource node ids

        for (PopResource pop : resources) {
            List<String> popNodes = new ArrayList<String>();
            for (NodeResource nodeSet : pop.getNodes()) {
                for (int i = 1; i <= nodeSet.getQuantity(); i++) {
                    popNodes.add(pop.getPopName() + "_" + nodeSet.getName() + "_" + i);
                }
                availableNodeCounter += nodeSet.getQuantity();
            }
            availableResourceNodes.add(popNodes);
        }
        // Warning: this lists will get consumed in the following steps!

        // Simply map the list of instance nodes to the lists of resource nodes
        List<String> unitNodeNames = new ArrayList<String>();
/*        for(UnitInstance unitInstance: instance.units) {
            unitNodeNames.add(unitInstance.name);
        }*/

        for (Map.Entry<String, Map<String, FunctionInstance>> vnf_instances : instance.function_list.entrySet()) {
            for (Map.Entry<String, FunctionInstance> finst : vnf_instances.getValue().entrySet()) {
                unitNodeNames.add(finst.getValue().deploymentUnits.get(0).getId());
            }
        }


        assert availableNodeCounter >= unitNodeNames.size() : "Datacenter do not have enough nodes.";
        int currentDatacenterIndex = 0;
        for (int i = 0; i < unitNodeNames.size(); i++) {

            List<String> availableNodes;

            // Search for datacenter with available nodes
            do {
                availableNodes = availableResourceNodes.get(currentDatacenterIndex);
                if (availableNodes.isEmpty())
                    currentDatacenterIndex++;
            } while (availableNodes.isEmpty());

            mapping.mapping.put(unitNodeNames.get(i), availableNodes.remove(0));
            mapping.popMapping.put(unitNodeNames.get(i), resources.get(currentDatacenterIndex));
        }

        // TODO: Ignoring network resources for now

        return mapping;
    }

    @Override
    public PlacementMapping updatePlacement(DeployServiceData serviceData, ServiceInstance instance, List<PopResource> ressources, PlacementMapping mapping) {

        // TODO: implement placement update for scale out/in
        return null;
    }
}
