package sonata.kernel.placement;

import org.apache.log4j.Logger;
import sonata.kernel.placement.config.PlacementConfig;
import sonata.kernel.placement.config.PopResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DatacenterManager {
    final static Logger logger = Logger.getLogger(DatacenterManager.class);
    static PlacementConfig config;
    static HashMap<String, DatacenterResource> m_datacenters;

    public DatacenterManager()
    {

    }

    public static void initialize()
    {
        config = PlacementConfigLoader.loadPlacementConfig();
        m_datacenters = new HashMap<String, DatacenterResource>();

        for(PopResource resource : config.getResources())
        {
            m_datacenters.put(resource.getPopName(),
                    new DatacenterResource(resource.getPopName(),
                            resource.getResource().cpu,
                            resource.getResource().memory,
                            resource.getResource().storage));
        }
    }


    public static int get_total_cpu(String datacenter)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_total_cpu: Unknown datacenter: " + datacenter);
            return 0;
        }
        else
            return DatacenterManager.m_datacenters.get(datacenter).total_cpu;

    }

    public static double get_total_memory(String datacenter)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_total_memory: Unknown datacenter: " + datacenter);
            return 0;
        }
        else
            return DatacenterManager.m_datacenters.get(datacenter).total_memory;

    }

    public static double get_total_storage(String datacenter)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_total_storage: Unknown datacenter: " + datacenter);
            return 0;
        }
        else
            return DatacenterManager.m_datacenters.get(datacenter).total_storage;

    }

    public static int get_available_cpu(String datacenter)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_available_cpu: Unknown datacenter: " + datacenter);
            return 0;
        }
        else
            return DatacenterManager.m_datacenters.get(datacenter).available_cpu;

    }

    public static double get_available_memory(String datacenter)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_available_memory: Unknown datacenter: " + datacenter);
            return 0;
        }
        else
            return DatacenterManager.m_datacenters.get(datacenter).available_memory;

    }

    public static double get_available_storage(String datacenter)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_available_storage: Unknown datacenter: " + datacenter);
            return 0;
        }
        else
            return DatacenterManager.m_datacenters.get(datacenter).available_storage;

    }

    public static boolean consume_storage(String datacenter, double amount)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::consume_storage: Unknown datacenter: " + datacenter);
            return false;
        }

        if(DatacenterManager.m_datacenters.get(datacenter).available_storage < amount)
        {
            logger.error("DatacenterManager::consume_storage: Insufficient storage available on : " + datacenter);
            return false;
        } else {
            DatacenterManager.m_datacenters.get(datacenter).available_storage
                    = DatacenterManager.m_datacenters.get(datacenter).available_storage - amount;
            return true;
        }
    }

    public static boolean consume_memory(String datacenter, double amount)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::consume_memory: Unknown datacenter: " + datacenter);
            return false;
        }

        if(DatacenterManager.m_datacenters.get(datacenter).available_memory < amount)
        {
            logger.error("DatacenterManager::consume_memory: Insufficient memory available on : " + datacenter);
            return false;
        } else {
                DatacenterManager.m_datacenters.get(datacenter).available_memory
                        = DatacenterManager.m_datacenters.get(datacenter).available_memory - amount;
            return true;
        }
    }

    public static boolean consume_cpu(String datacenter, int amount)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::consume_cpu: Unknown datacenter: " + datacenter);
            return false;
        }

        if(DatacenterManager.m_datacenters.get(datacenter).available_cpu < amount)
        {
            logger.error("DatacenterManager::consume_cpu: Insufficient cpu available on : " + datacenter);
            return false;
        } else {
            DatacenterManager.m_datacenters.get(datacenter).available_cpu
                    = DatacenterManager.m_datacenters.get(datacenter).available_cpu - amount;
            return true;
        }
    }

    public static void relinquish_cpu(String datacenter, int cpu)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::relinquish_cpu: Unknown datacenter: " + datacenter);
            return;
        }

        DatacenterManager.m_datacenters.get(datacenter).available_cpu =
                DatacenterManager.m_datacenters.get(datacenter).available_cpu + cpu;
    }

    public static void relinquish_memory(String datacenter, double memory)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::relinquish_memory: Unknown datacenter: " + datacenter);
            return;
        }

        DatacenterManager.m_datacenters.get(datacenter).available_memory =
                DatacenterManager.m_datacenters.get(datacenter).available_memory + memory;
    }

    public static void relinquish_storage(String datacenter, double storage)
    {
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::relinquish_storage: Unknown datacenter: " + datacenter);
            return;
        }

        DatacenterManager.m_datacenters.get(datacenter).available_storage =
                DatacenterManager.m_datacenters.get(datacenter).available_storage + storage;
    }
}


class DatacenterResource
{
    String label;
    int total_cpu;
    double total_memory;
    double available_memory;
    int available_cpu;
    double total_storage;
    double available_storage;

    public DatacenterResource(String label, int cpu, double memory, double storage)
    {
        this.label = label;
        this.total_cpu = cpu;
        this.total_memory = memory;
        this.available_cpu = cpu;
        this.available_memory = memory;
        this.total_storage = storage;
        this.available_storage = storage;
    }
}

