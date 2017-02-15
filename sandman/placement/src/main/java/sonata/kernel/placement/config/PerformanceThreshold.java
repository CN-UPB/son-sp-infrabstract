package sonata.kernel.placement.config;

/**
 * Define thresholds for out/in-scaling of a vnf.
 * All thresholds are point numbers between 0 and 100 and depict percentages.
 */
public class PerformanceThreshold {

    public double getCpu_upper_l() {
        return cpu_upper_l;
    }

    public void setCpu_upper_l(double cpu_upper_l) {
        this.cpu_upper_l = cpu_upper_l;
    }

    public double getCpu_lower_l() {
        return cpu_lower_l;
    }

    public void setCpu_lower_l(double cpu_lower_l) {
        this.cpu_lower_l = cpu_lower_l;
    }

    public float getMem_upper_l() {
        return mem_upper_l;
    }

    public void setMem_upper_l(float mem_uppper_l) {
        this.mem_upper_l = mem_uppper_l;
    }

    public float getMem_lower_l() {
        return mem_lower_l;
    }

    public void setMem_lower_l(float mem_lower_l) {
        this.mem_lower_l = mem_lower_l;
    }

    public String getVnfId() {
        return vnfId;
    }

    public void setVnfId(String vnfId) {
        this.vnfId = vnfId;
    }

    public double getScale_out_upper_l() {
        return scale_out_upper_l;
    }

    public void setScale_out_upper_l(double scale_out_upper_l) {
        this.scale_out_upper_l = scale_out_upper_l;
    }

    public double getScale_in_lower_l() {
        return scale_in_lower_l;
    }

    public void setScale_in_lower_l(double scale_in_lower_l) {
        this.scale_in_lower_l = scale_in_lower_l;
    }

    public int getHistory_check() {
        return history_check;
    }

    public void setHistory_check(int history_check) {
        this.history_check = history_check;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("CPU Upper Limit: " + cpu_upper_l + "\n");
        builder.append("CPU Lower Limit: " + cpu_lower_l + "\n");
        builder.append("Memory Upper Limit: " + mem_upper_l + "\n");
        builder.append("Memory Lower Limit: " + mem_lower_l + "\n");
        builder.append("Scale Out Upper Limit: " + scale_out_upper_l + "\n");
        builder.append("Scale In Lower Limit: " + scale_in_lower_l + "\n");
        builder.append("Number of history checks: " + history_check + "\n");
        builder.append("VNF ID: " + vnfId);
        return builder.toString();
    }

    /**
     * Upper threshold for CPU load
     */
    double cpu_upper_l;
    /**
     * Lower threshold for CPU load
     */
    double cpu_lower_l;
    /**
     * Upper threshold for memory usage
     */
    float mem_upper_l;
    /**
     * Lower threshold for memory usage
     */
    float mem_lower_l;
    /**
     * Percentage of vnfs that are allowed to exceed the limits before scale out
     */
    double scale_out_upper_l;
    /**
     * Percentage of vnfs that are allowed to stay under the limits before scale in
     */
    double scale_in_lower_l;
    /**
     * Number of monitoring samples to consider
     */
    int history_check;
    /**
     * Name of vnf
     */
    String vnfId;


}
