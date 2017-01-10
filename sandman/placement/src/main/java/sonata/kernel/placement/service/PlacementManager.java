package sonata.kernel.placement.service;


import org.apache.log4j.Logger;


public class PlacementManager {
    final static Logger logger = Logger.getLogger(PlacementManager.class);
    final private ServiceInstanceManager instance_manager;

    public PlacementManager() {
        this.instance_manager = new ServiceInstanceManager();
    }

    public Node GenerateServiceGraph()
    {
        ServiceGraph graph = new ServiceGraph(instance_manager.get_instance());
        Node node = graph.generate_graph();
        return node;
    }


    public boolean AddVirtualLink(String SourceVnfInstance, String TargetVnfInstance)
    {
        String SourceVnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(SourceVnfInstance);

        if(SourceVnfId == null)
        {
            logger.fatal("PlacementManager::AddVirtualLink: Unable to add link to "
                    + SourceVnfInstance + ". Unknown VnfId.");
            return false;
        }

        String TargetVnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(TargetVnfInstance);
        if(TargetVnfId == null)
        {
            logger.fatal("PlacementManager::AddVirtualLink: Unable to add link to "
                    + TargetVnfInstance + ". Unknown VnfId.");
            return false;
        }

        instance_manager.update_vlink_list("vnf_firewall", "vnf_tcpdump", "vnf_firewall1", "vnf_tcpdump1",
                ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

        return true;
    }

    public boolean DeleteVirtualLink(String SourceVnfInstance, String TargetVnfInstance)
    {
        String SourceVnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(SourceVnfInstance);
        if(SourceVnfId == null)
        {
            logger.fatal("PlacementManager::DeleteVirtualLink: Unable to delete link to "
                    + SourceVnfInstance + ". Unknown VnfId.");
            return false;
        }

        String TargetVnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(TargetVnfInstance);
        if(TargetVnfId == null)
        {
            logger.fatal("PlacementManager::DeleteVirtualLink: Unable to delete link to "
                    + TargetVnfInstance + ". Unknown VnfId.");
            return false;
        }

        this.instance_manager.update_vlink_list(SourceVnfId, TargetVnfId, SourceVnfInstance, TargetVnfInstance,
                ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);
        return true;
    }

    public String AddNetworkFunctionInstance(String VnfId)
    {
        String VnfInstanceName = this.instance_manager.update_functions_list(VnfId, null, ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);
        if(VnfInstanceName == null)
        {
            logger.fatal("PlacementManager::AddNetworkFunctionInstance: Failed to add instance for Network Function " + VnfId);
            return null;
        }
        return VnfInstanceName;
    }

    public boolean DeleteNetworkFunctionInstance(String VnfInstance)
    {
        String VnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(VnfInstance);
        if(VnfId != null)
            this.instance_manager.update_functions_list(VnfId, VnfInstance, ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);
        else {
            logger.fatal("PlacementManager::DeleteNetworkFunctionInstance: Unable to delete function instance "
                    + VnfInstance + ". Unknown VnfId.");
            return false;
        }

        return true;
    }

    public boolean MoveNetworkFunctionInstance(String VnfInstance, String PopName)
    {
        String VnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(VnfInstance);
        if(VnfId == null)
        {
            logger.fatal("PlacementManager::MoveNetworkFunctionInstance: Unknown Vnf instance.");
            return false;
        }


        return true;
    }

}
