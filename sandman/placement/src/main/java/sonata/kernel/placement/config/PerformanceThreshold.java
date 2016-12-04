package sonata.kernel.placement.config;


public class PerformanceThreshold {

    public int getCpu_upper_l() {
        return cpu_upper_l;
    }

    public void setCpu_upper_l(int cpu_upper_l) {
        this.cpu_upper_l = cpu_upper_l;
    }

    public int getCpu_lower_l() {
        return cpu_lower_l;
    }

    public void setCpu_lower_l(int cpu_lower_l) {
        this.cpu_lower_l = cpu_lower_l;
    }

    public int getMem_upper_l() {
        return mem_upper_l;
    }

    public void setMem_upper_l(int mem_uppper_l) {
        this.mem_upper_l = mem_uppper_l;
    }

    public int getMem_lower_l() {
        return mem_lower_l;
    }

    public void setMem_lower_l(int mem_lower_l) {
        this.mem_lower_l = mem_lower_l;
    }

    public String getVnfId() {
        return vnfId;
    }

    public void setVnfId(String vnfId) {
        this.vnfId = vnfId;
    }

    int cpu_upper_l;
    int cpu_lower_l;
    int mem_upper_l;
    int mem_lower_l;
    String vnfId;


}
