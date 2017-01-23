package sonata.kernel.placement.service;


import org.apache.log4j.Logger;
import sonata.kernel.placement.PlacementConfigLoader;
import sonata.kernel.placement.config.PlacementConfig;
import sonata.kernel.placement.config.PopResource;
import sonata.kernel.placement.config.SystemResource;

import java.util.ArrayList;
import java.util.List;


public class PlacementManager {
    final static Logger logger = Logger.getLogger(PlacementManager.class);
    final private ServiceInstanceManager instance_manager;
    final private PlacementConfig config;

    public PlacementManager() {
        this.instance_manager = new ServiceInstanceManager();
        config = PlacementConfigLoader.loadPlacementConfig();
    }


    /**
     * This method returns the current Network Topology graph.
     * @return NetworkNode The root node of the topology graph.
     */
    public NetworkNode GenerateNetworkTopologyGraph()
    {
        logger.debug("PlacementManager::GenerateServiceGraph ENTER");
        String graph_text = "";

        NetworkTopologyGraph t_graph = new NetworkTopologyGraph();
        NetworkNode root = t_graph.generate_graph();
        if(null == root)
        {
            logger.error("PlacementManager::GenerateNetworkTopologyGraph: Topology Graph unavailable");
            logger.debug("PlacementManager::GenerateServiceGraph EXIT");
            return null;
        }
        logger.debug("PlacementManager::GenerateServiceGraph EXIT");
        return root;
    }

    /**
     * This method generates the service graph associated with the current Service Instance.
     * @return Node the root of the service graph.
     */
    public Node GenerateServiceGraph()
    {
        logger.debug("PlacementManager::GenerateServiceGraph ENTER");
        ServiceGraph graph = new ServiceGraph(instance_manager.get_instance());
        Node node = graph.generate_graph();
        logger.debug("PlacementManager::GenerateServiceGraph EXIT");
        return node;
    }

    /**
     * This method adds a link between two VNF instances.
     * @param SourceVnfInstance The Source VNF instance.
     * @param TargetVnfInstance The Target VNF instance.
     * @param viaPath The customized routing path (Eg: tcpdump1-s1-s2-firwall1 viaPath = s1:s2).
     * @return Boolean Status of the link addition.
     */
    public boolean AddVirtualLink(String SourceVnfInstance, String TargetVnfInstance, String viaPath)
    {
        logger.debug("PlacementManager::AddVirtualLink ENTER");
        logger.info("PlacementManager::AddVirtualLink: Source VnfInstance: " + SourceVnfInstance
                + " Target VnfInstance: " + TargetVnfInstance);

        String SourceVnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(SourceVnfInstance);

        if(SourceVnfId == null)
        {
            logger.fatal("PlacementManager::AddVirtualLink: Unable to add link to "
                    + SourceVnfInstance + ". Unknown VnfId.");
            logger.debug("PlacementManager::AddVirtualLink EXIT");
            return false;
        }

        String TargetVnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(TargetVnfInstance);
        if(TargetVnfId == null)
        {
            logger.fatal("PlacementManager::AddVirtualLink: Unable to add link to "
                    + TargetVnfInstance + ". Unknown VnfId.");
            logger.debug("PlacementManager::AddVirtualLink EXIT");
            return false;
        }

        instance_manager.update_vlink_list("vnf_firewall", "vnf_tcpdump", "vnf_firewall1", "vnf_tcpdump1",
                ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);

        if(viaPath != null || !viaPath.equals(""))
        {
            //Delete the default chaining rule and add the customized chaining rule.
        }

        logger.debug("PlacementManager::AddVirtualLink EXIT");
        return true;
    }

    /**
     * This method deletes a link between two VNF instances.
     * @param SourceVnfInstance The Source VNF instance.
     * @param TargetVnfInstance The Target VNF instance.
     * @return Boolean Status of the link deletion.
     */
    public boolean DeleteVirtualLink(String SourceVnfInstance, String TargetVnfInstance)
    {
        logger.debug("PlacementManager::DeleteVirtualLink ENTRY");
        logger.info("PlacementManager::DeleteVirtualLink: Source VnfInstance: " + SourceVnfInstance
                + " Target VnfInstance: " + TargetVnfInstance);

        String SourceVnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(SourceVnfInstance);
        if(SourceVnfId == null)
        {
            logger.fatal("PlacementManager::DeleteVirtualLink: Unable to delete link to "
                    + SourceVnfInstance + ". Unknown VnfId.");
            logger.debug("PlacementManager::DeleteVirtualLink EXIT");
            return false;
        }

        String TargetVnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(TargetVnfInstance);
        if(TargetVnfId == null)
        {
            logger.fatal("PlacementManager::DeleteVirtualLink: Unable to delete link to "
                    + TargetVnfInstance + ". Unknown VnfId.");
            logger.debug("PlacementManager::DeleteVirtualLink EXIT");
            return false;
        }

        this.instance_manager.update_vlink_list(SourceVnfId, TargetVnfId, SourceVnfInstance, TargetVnfInstance,
                ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);
        logger.debug("PlacementManager::DeleteVirtualLink EXIT");
        return true;
    }

    /**
     * This method adds a new instance of a VNF.
     * @param VnfId String identifying the VNF ID if the VNF instance to be added.
     * @param PopName String identifying the PoP where the instance must be deployed.
     * @return String The name of the VNF instance.
     */
    public String AddNetworkFunctionInstance(String VnfId, String PopName)
    {
        logger.debug("PlacementManager::AddNetworkFunctionInstance ENTRY");
        logger.info("PlacementManager::AddNetworkFunctionInstance: VnfId: " + VnfId);

        String VnfInstanceName = this.instance_manager.update_functions_list(VnfId, null, PopName, ServiceInstanceManager.ACTION_TYPE.ADD_INSTANCE);
        if(VnfInstanceName == null)
        {
            logger.fatal("PlacementManager::AddNetworkFunctionInstance: Failed to add instance for Network Function " + VnfId);
            logger.debug("PlacementManager::AddNetworkFunctionInstance EXIT");
            return null;
        }
        logger.debug("PlacementManager::AddNetworkFunctionInstance EXIT");
        return VnfInstanceName;
    }

    /**
     * This method deletes an existing instance of a VNF.
     * @param VnfInstance String identifying the VNF instance to be deleted.
     * @return boolean Status of the VNF instance deletion.
     */
    public boolean DeleteNetworkFunctionInstance(String VnfInstance)
    {
        logger.debug("PlacementManager::DeleteNetworkFunctionInstance ENTRY");
        logger.info("PlacementManager::DeleteNetworkFunctionInstance: VnfInstance: " + VnfInstance);

        String VnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(VnfInstance);
        if(VnfId != null)
            this.instance_manager.update_functions_list(VnfId, VnfInstance, null, ServiceInstanceManager.ACTION_TYPE.DELETE_INSTANCE);
        else {
            logger.fatal("PlacementManager::DeleteNetworkFunctionInstance: Unable to delete function instance "
                    + VnfInstance + ". Unknown VnfId.");
            logger.debug("PlacementManager::DeleteNetworkFunctionInstance EXIT");
            return false;
        }

        logger.debug("PlacementManager::DeleteNetworkFunctionInstance EXIT");
        return true;
    }

    /**
     * This method migrates an existing instance of a VNF to another PoP.
     * @param VnfInstance String identifying the VNF instance that needs to be moved.
     * @param PopName String identifying the PoP to which the VNF instance has to be migrated.
     * @return boolean Status of the VNF instance migration.
     */
    public boolean MoveNetworkFunctionInstance(String VnfInstance, String PopName)
    {
        logger.debug("PlacementManager::MoveNetworkFunctionInstance ENTRY");
        logger.info("PlacementManager::MoveNetworkFunctionInstance: VnfInstance: " + VnfInstance + " PopName: " + PopName);

        String VnfId = this.instance_manager.get_instance().findVnfIdFromVnfInstanceName(VnfInstance);
        if(VnfId == null)
        {
            logger.fatal("PlacementManager::MoveNetworkFunctionInstance: Unknown Vnf instance.");
            logger.debug("PlacementManager::MoveNetworkFunctionInstance EXIT");
            return false;
        }

        logger.debug("PlacementManager::MoveNetworkFunctionInstance EXIT");
        return this.instance_manager.get_instance().updateDataCenterForVnfInstance(VnfInstance, PopName);
    }

    /**
     * This method returns the total available PoP's.
     * @return List String List of available PoP names.
     */
    public List<String> GetAvailablePoP()
    {
        logger.debug("PlacementManager::GetAvailablePoP ENTRY");
        List<String> PopList = new ArrayList<String>();

        ArrayList<PopResource> resource_list = config.getResources();
        for(PopResource resource : resource_list)
        {
            PopList.add(resource.getPopName());
        }
        logger.info("PlacementManager::GetAvailablePoP: Found " + PopList.size() + " PoP's");
        logger.debug("PlacementManager::GetAvailablePoP EXIT");
        return PopList;
    }

    /**
     * This method returns the total VNF instance capacity on the PoP.
     * @param PopName String identifying the PoP.
     * @return int Total VNF capacity of the PoP.

    public int GetTotalPoPCapacity(String PopName)
    {
        logger.debug("PlacementManager::GetTotalPoPCapacity ENTRY");
        logger.info("PlacementManager::GetTotalPoPCapacity: PopName: " + PopName);

        ArrayList<PopResource> resource_list = config.getResources();
        for(PopResource resource : resource_list)
        {
            if(resource.getPopName().equals(PopName)) {
                logger.info("PlacementManager::GetTotalPopCapacity: Capacity = " + resource.getNodes().size());
                logger.debug("PlacementManager::GetTotalPoPCapacity EXIT");
                return resource.getNodes().size();
            }
        }
        logger.error("PlacementManager::GetTotalPoPCapacity: Cannot find PoP: " + PopName);
        logger.debug("PlacementManager::GetTotalPoPCapacity EXIT");
        return 0;
    }*/

    /**
     * This method returns the total CPU capacity on the PoP.
     * @param PopName String identifying the PoP.
     * @return int Total CPU capacity of the PoP.
     */
    public int GetTotalCPU(String PopName)
    {
        logger.debug("PlacementManager::GetTotalCPU ENTRY");
        logger.info("PlacementManager::GetTotalCPU: PopName: " + PopName);

        ArrayList<PopResource> resource_list = config.getResources();
        for(PopResource resource : resource_list)
        {
            if(resource.getPopName().equals(PopName)) {
                logger.info("PlacementManager::GetTotalCPU: TotalCPU = " + resource.getResource().cpu);
                logger.debug("PlacementManager::GetTotalCPU EXIT");
                return resource.getResource().cpu;
            }
        }
        logger.error("PlacementManager::GetTotalCPU: Cannot find PoP: " + PopName);
        logger.debug("PlacementManager::GetTotalCPU EXIT");
        return 0;

    }

    /**
     * This method returns the unused CPU capacity on the PoP.
     * @param PopName String identifying the PoP.
     * @return int Unused CPU capacity of the PoP.
     */
    public int GetAvailableCPU(String PopName)
    {
        logger.debug("PlacementManager::GetAvailableCPU ENTRY");
        logger.info("PlacementManager::GetAvailableCPU: PopName: " + PopName);

        ArrayList<PopResource> resource_list = config.getResources();
        for(PopResource resource : resource_list)
        {
            if(resource.getPopName().equals(PopName)) {
                logger.info("PlacementManager::GetAvailableCPU: AvailableCPU = " + (resource.getResource().cpu - resource.getResource().getCpu_used()));
                logger.debug("PlacementManager::GetAvailableCPU EXIT");
                return resource.getResource().cpu;
            }
        }
        logger.error("PlacementManager::GetAvailableCPU: Cannot find PoP: " + PopName);
        logger.debug("PlacementManager::GetAvailableCPU EXIT");
        return 0;

    }

    /**
     * This method returns the total memory capacity on the PoP.
     * @param PopName String identifying the PoP.
     * @return int Unused memory capacity of the PoP.
     */
    public int GetAvailableMemory(String PopName)
    {
        logger.debug("PlacementManager::GetAvailableMemory ENTRY");
        logger.info("PlacementManager::GetAvailableMemory: PopName: " + PopName);

        ArrayList<PopResource> resource_list = config.getResources();
        for(PopResource resource : resource_list)
        {
            if(resource.getPopName().equals(PopName)) {
                logger.info("PlacementManager::GetAvailableMemory: TotalMemory = " + (resource.getResource().memory - resource.getResource().getMemory_used()));
                logger.debug("PlacementManager::GetAvailableMemory EXIT");
                return resource.getResource().memory;
            }
        }
        logger.error("PlacementManager::GetAvailableMemory: Cannot find PoP: " + PopName);
        logger.debug("PlacementManager::GetAvailableMemory EXIT");
        return 0;
    }

    /**
     * This method returns the total memory capacity on the PoP.
     * @param PopName String identifying the PoP.
     * @return int Total memory capacity of the PoP.
     */
    public int GetTotalMemory(String PopName)
    {
        logger.debug("PlacementManager::GetTotalMemory ENTRY");
        logger.info("PlacementManager::GetTotalMemory: PopName: " + PopName);

        ArrayList<PopResource> resource_list = config.getResources();
        for(PopResource resource : resource_list)
        {
            if(resource.getPopName().equals(PopName)) {
                logger.info("PlacementManager::GetTotalMemory: TotalMemory = " + resource.getResource().memory);
                logger.debug("PlacementManager::GetTotalMemory EXIT");
                return resource.getResource().memory;
            }
        }
        logger.error("PlacementManager::GetTotalMemory: Cannot find PoP: " + PopName);
        logger.debug("PlacementManager::GetTotalMemory EXIT");
        return 0;

    }



    /*
     * This method returns the free VNF instance capacity on the PoP.
     * @param PopName String identifying the PoP.
     * @return int Total VNF capacity of the PoP.
     *
    public int GetAvailablePoPCapacity(String PopName)
    {
        logger.debug("PlacementManager::GetAvailablePoPCapacity ENTRY");
        logger.info("PlacementManager::GetAvailablePoPCapacity: PopName: " + PopName);

        ArrayList<PopResource> resource_list = config.getResources();
        for(PopResource resource : resource_list)
        {

        }

        logger.error("PlacementManager::GetAvailablePoPCapacity: Cannot find PoP: " + PopName);
        logger.debug("PlacementManager::GetAvailablePoPCapacity EXIT");
        return 0;
    }*/


}
