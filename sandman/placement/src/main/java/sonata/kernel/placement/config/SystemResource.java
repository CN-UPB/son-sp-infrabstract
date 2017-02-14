package sonata.kernel.placement.config;

/**
 * Resources served by a datacenter
 */
public class SystemResource {

    /**
     * Number of cpus
     */
    public int cpu;
    /**
     * Amount of memory in GB
     */
    public double memory;
    /**
     * Amount of storage in GB
     */
    public double storage;

    public int getCpu() {
        return cpu;
    }

    public void setCpu(int cpu) {
        this.cpu = cpu;
    }

    public double getMemory() {
        return memory;
    }

    public void setMemory(double memory) {
        this.memory = memory;
    }

    public double getStorage()
    {
        return storage;
    }

    public void setStorage(double storage)
    {
        this.storage = storage;
    }


}
