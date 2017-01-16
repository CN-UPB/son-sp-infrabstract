package sonata.kernel.placement.config;


public class SystemResource {

    public int cpu;
    public int memory;

    public int getCpu_used() {
        return cpu_used;
    }

    public void setCpu_used(int cpu_used) {
        this.cpu_used = cpu_used;
    }

    public int getMemory_used() {
        return memory_used;
    }

    public void setMemory_used(int memory_used) {
        this.memory_used = memory_used;
    }

    private int cpu_used;
    private int memory_used;

    public int getCpu() {
        return cpu;
    }

    public void setCpu(int cpu) {
        this.cpu = cpu;
    }

    public int getMemory() {
        return memory;
    }

    public void setMemory(int memory) {
        this.memory = memory;
    }


}
