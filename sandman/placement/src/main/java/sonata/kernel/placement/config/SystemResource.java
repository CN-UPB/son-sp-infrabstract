package sonata.kernel.placement.config;


public class SystemResource {

    public int cpu;
    public double memory;
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
