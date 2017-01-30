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
        logger.debug("DatacenterManager::initialize ENTRY");
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
        logger.debug("DatacenterManager::initialize EXIT");
    }

    public static boolean check_datacenter_exists(String datacenter)
    {
        if(m_datacenters.get(datacenter) == null)
            return false;
        return true;
    }
    public static int get_total_cpu(String datacenter)
    {
        logger.debug("DatacenterManager::get_total_cpu ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_total_cpu: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::get_total_cpu EXIT");
            return 0;
        }
        else {
            logger.info("DatacenterManager::get_total_cpu : " + datacenter
                    + ": " + DatacenterManager.m_datacenters.get(datacenter).total_cpu);
            logger.debug("DatacenterManager::get_total_cpu EXIT");
            return DatacenterManager.m_datacenters.get(datacenter).total_cpu;
        }
    }

    public static double get_total_memory(String datacenter)
    {
        logger.debug("DatacenterManager::get_total_memory ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_total_memory: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::get_total_memory EXIT");
            return 0;
        }
        else {
            logger.info("DatacenterManager::get_total_memory : " + datacenter
                    + ": " + DatacenterManager.m_datacenters.get(datacenter).total_memory);
            logger.debug("DatacenterManager::get_total_memory EXIT");
            return DatacenterManager.m_datacenters.get(datacenter).total_memory;
        }

    }

    public static double get_total_storage(String datacenter)
    {
        logger.debug("DatacenterManager::get_total_storage ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_total_storage: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::get_total_storage EXIT");
            return 0;
        }
        else {
            logger.info("DatacenterManager::get_total_storage : " + datacenter
                    + ": " + DatacenterManager.m_datacenters.get(datacenter).total_storage);
            logger.debug("DatacenterManager::get_total_storage EXIT");
            return DatacenterManager.m_datacenters.get(datacenter).total_storage;
        }

    }

    public static int get_available_cpu(String datacenter)
    {
        logger.debug("DatacenterManager::get_available_cpu ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_available_cpu: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::get_available_cpu EXIT");
            return 0;
        }
        else {
            logger.info("DatacenterManager::get_available_cpu : " + datacenter
                    + ": " + DatacenterManager.m_datacenters.get(datacenter).available_cpu);
            logger.debug("DatacenterManager::get_available_cpu ENTRY");
            return DatacenterManager.m_datacenters.get(datacenter).available_cpu;
        }

    }

    public static double get_available_memory(String datacenter)
    {
        logger.debug("DatacenterManager::get_available_memory ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_available_memory: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::get_available_memory ENTRY");
            return 0;
        }
        else {
            logger.info("DatacenterManager::get_available_memory : " + datacenter
                    + ": " + DatacenterManager.m_datacenters.get(datacenter).available_memory);
            logger.debug("DatacenterManager::get_available_memory EXIT");
            return DatacenterManager.m_datacenters.get(datacenter).available_memory;
        }

    }

    public static double get_available_storage(String datacenter)
    {
        logger.debug("DatacenterManager::get_available_storage ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::get_available_storage: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::get_available_storage EXIT");
            return 0;
        }
        else {
            logger.info("DatacenterManager::get_available_storage : " + datacenter
                    + ": " + DatacenterManager.m_datacenters.get(datacenter).available_storage);
            logger.debug("DatacenterManager::get_available_storage EXIT");
            return DatacenterManager.m_datacenters.get(datacenter).available_storage;
        }

    }

    public static boolean consume_storage(String datacenter, double amount)
    {
        logger.debug("DatacenterManager::consume_storage ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::consume_storage: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::consume_storage EXIT");
            return false;
        }

        if(DatacenterManager.m_datacenters.get(datacenter).available_storage < amount)
        {
            logger.error("DatacenterManager::consume_storage: Insufficient storage available on : " + datacenter);
            logger.debug("DatacenterManager::consume_storage EXIT");
            return false;
        } else {
            DatacenterManager.m_datacenters.get(datacenter).available_storage
                    = DatacenterManager.m_datacenters.get(datacenter).available_storage - amount;
            logger.debug("DatacenterManager::consume_storage EXIT");
            return true;
        }
    }

    public static boolean consume_memory(String datacenter, double amount)
    {
        logger.debug("DatacenterManager::consume_memory ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::consume_memory: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::consume_memory EXIT");
            return false;
        }

        if(DatacenterManager.m_datacenters.get(datacenter).available_memory < amount)
        {
            logger.error("DatacenterManager::consume_memory: Insufficient memory available on : " + datacenter);
            logger.debug("DatacenterManager::consume_memory EXIT");
            return false;
        } else {
                DatacenterManager.m_datacenters.get(datacenter).available_memory
                        = DatacenterManager.m_datacenters.get(datacenter).available_memory - amount;
            logger.debug("DatacenterManager::consume_memory EXIT");
            return true;
        }
    }

    public static boolean consume_cpu(String datacenter, int amount)
    {
        logger.debug("DatacenterManager::consume_cpu ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::consume_cpu: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::consume_cpu EXIT");
            return false;
        }

        if(DatacenterManager.m_datacenters.get(datacenter).available_cpu < amount)
        {
            logger.error("DatacenterManager::consume_cpu: Insufficient cpu available on : " + datacenter);
            logger.debug("DatacenterManager::consume_cpu EXIT");
            return false;
        } else {
            DatacenterManager.m_datacenters.get(datacenter).available_cpu
                    = DatacenterManager.m_datacenters.get(datacenter).available_cpu - amount;
            logger.debug("DatacenterManager::consume_cpu EXIT");
            return true;
        }
    }

    public static void relinquish_cpu(String datacenter, int cpu)
    {
        logger.debug("DatacenterManager::relinquish_cpu ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::relinquish_cpu: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::relinquish_cpu EXIT");
            return;
        }

        DatacenterManager.m_datacenters.get(datacenter).available_cpu =
                DatacenterManager.m_datacenters.get(datacenter).available_cpu + cpu;
        logger.debug("DatacenterManager::relinquish_cpu EXIT");
        return;
    }

    public static void relinquish_memory(String datacenter, double memory)
    {
        logger.debug("DatacenterManager::relinquish_memory ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::relinquish_memory: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::relinquish_memory EXIT");
            return;
        }

        DatacenterManager.m_datacenters.get(datacenter).available_memory =
                DatacenterManager.m_datacenters.get(datacenter).available_memory + memory;
        logger.debug("DatacenterManager::relinquish_memory EXIT");
        return;
    }

    public static void relinquish_storage(String datacenter, double storage)
    {
        logger.debug("DatacenterManager::relinquish_storage ENTRY");
        if(null == DatacenterManager.m_datacenters.get(datacenter)) {
            logger.error("DatacenterManager::relinquish_storage: Unknown datacenter: " + datacenter);
            logger.debug("DatacenterManager::relinquish_storage EXIT");
            return;
        }

        DatacenterManager.m_datacenters.get(datacenter).available_storage =
                DatacenterManager.m_datacenters.get(datacenter).available_storage + storage;
        logger.debug("DatacenterManager::relinquish_storage ENTRY");
        return;
    }

    public static void reset_resources()
    {
        logger.debug("DatacenterManager::reset_resources ENTRY");
        m_datacenters.clear();
        initialize();
        logger.debug("DatacenterManager::reset_resources EXIT");
        return;
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

