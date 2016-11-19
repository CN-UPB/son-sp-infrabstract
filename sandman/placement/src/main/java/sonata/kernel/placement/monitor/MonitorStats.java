package sonata.kernel.placement.monitor;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MonitorStats {

    /* example
    {'MEM_used': 790528, 'PIDS': 2, 'BLOCK_out': [], 'NET_out': 648,
     'SYS_time': 1479522691737230080L, 'MEM_limit': 16827117568,
     'NET_in': 648, 'BLOCK_in': ['Total', '0'], 'MEM_%': 4.6979406710947397e-05, 'CPU_%': 0.0}
    */

    // Network traffic of all network interfaces within the controller
    @JsonProperty("NET_out")
    long netOut;
    @JsonProperty("NET_in")
    long netIn;
    @JsonProperty("BLOCK_out") // Disk - write in Bytes
    String[] blockOut;
    @JsonProperty("BLOCK_in") // Disk - read in Bytes
    String[] blockIn;
    @JsonProperty("MEM_used") // Bytes of memory used from the docker container
    long memoryUsed;
    @JsonProperty("MEM_limit") // Bytes of memory the docker container could use
    long memoryLimit;
    @JsonProperty("MEM_%")
    float memoryPercentage;
    @JsonProperty("PIDS") // Number of processes
    int processes;
    @JsonProperty("CPU_%")
    double cpu;
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

    public String[] getBlockOut() {
        return blockOut;
    }

    public void setBlockOut(String[] blockOut) {
        this.blockOut = blockOut;
    }

    public String[] getBlockIn() {
        return blockIn;
    }

    public void setBlockIn(String[] blockIn) {
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
}
