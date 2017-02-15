package sonata.kernel.placement.monitor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contains monitored performance details about a vnf
 * The attributes are returned from the monitoring call that
 * takes two monitoring samples at an interval of 1 second
 * and computes the difference.
 */
public class MonitorStats {

    /* example JSON
    {"BLOCK_write/s": 0, "CPU_cores": 4,
    "SYS_time": 1481665658589828864, "PIDS": 2,
    "CPU_%": 0.0056449892973778545, "MEM_limit": 16827117568,
    "NET_out/s": 0, "NET_in/s": 0, "MEM_used": 704512,
    "BLOCK_read/s": 0, "MEM_%": 4.1867657794212184e-05}
    */

    /**
     * Number of bytes outgoing traffic
     * (All network interfaces within the controller)
     */
    @JsonProperty("NET_out/s")
    long netOut;
    /**
     * Number of bytes incoming traffic
     * (All network interfaces within the controller)
     */
    @JsonProperty("NET_in/s")
    long netIn;
    /**
     * Number of bytes written to disk
     */
    @JsonProperty("BLOCK_write/s") // Disk - write in Bytes
    String blockOut;
    /**
     * Number of bytes read from disk
     */
    @JsonProperty("BLOCK_read/s") // Disk - read in Bytes
    String blockIn;
    /**
     * Number of bytes used by the Docker container
     */
    @JsonProperty("MEM_used")
    long memoryUsed;
    /**
     * Maximum number of bytes the Docker container is allowed to use
     */
    @JsonProperty("MEM_limit")
    long memoryLimit;
    /**
     * Percentage of memory used
     * Minimum: 0.0
     * Maximum: 1.0
     * memoryPercentage = memoryUsed / memoryLimit
     */
    @JsonProperty("MEM_%")
    float memoryPercentage;
    /**
     * Number of processes in the container
     */
    @JsonProperty("PIDS")
    int processes;
    /**
     * Load of the CPU.
     * Minimum: 0.0
     * Maximum: 1.0 * Docker CPU limit
     */
    @JsonProperty("CPU_%")
    double cpu;
    /**
     * Number of maximum CPU cores assigned to the Docker container
     */
    @JsonProperty("CPU_cores")
    int cpuCores;
    /**
     * Timestamp on the emulator in nanoseconds
     */
    @JsonProperty("SYS_time")
    long sysTime;

    public long getNetOut() {
        return netOut;
    }

    public void setNetOut(long netOut) {
        this.netOut = netOut;
    }

    public long getNetIn() {
        return netIn;
    }

    public void setNetIn(long netIn) {
        this.netIn = netIn;
    }

    public String getBlockOut() {
        return blockOut;
    }

    public void setBlockOut(String blockOut) {
        this.blockOut = blockOut;
    }

    public String getBlockIn() {
        return blockIn;
    }

    public void setBlockIn(String blockIn) {
        this.blockIn = blockIn;
    }

    public long getMemoryUsed() {
        return memoryUsed;
    }

    public void setMemoryUsed(long memoryUsed) {
        this.memoryUsed = memoryUsed;
    }

    public long getMemoryLimit() {
        return memoryLimit;
    }

    public void setMemoryLimit(long memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    public float getMemoryPercentage() {
        return memoryPercentage;
    }

    public void setMemoryPercentage(float memoryPercentage) {
        this.memoryPercentage = memoryPercentage;
    }

    public int getProcesses() {
        return processes;
    }

    public void setProcesses(int processes) {
        this.processes = processes;
    }

    public double getCpu() {
        return cpu;
    }

    public void setCpu(double cpu) {
        this.cpu = cpu;
    }

    public long getSysTime() {
        return sysTime;
    }

    public void setSysTime(long sysTime) {
        this.sysTime = sysTime;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(int cpuCores) {
        this.cpuCores = cpuCores;
    }

}
