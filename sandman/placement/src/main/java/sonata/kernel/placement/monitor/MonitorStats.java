package sonata.kernel.placement.monitor;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MonitorStats {

    /* example
    {"BLOCK_read": 0, "NET_out": 1016, "CPU_cores": 4, "NET_systime": 1480879807587300096,
    "MEM_used": 630784, "PIDS": 2, "CPU_used_systime": 1480879807533447936, "NET_in": 1016,
    "SYS_time": 1480879807587481856, "CPU_used": 22920796, "BLOCK_write": 0,
    "MEM_limit": 16827117568, "MEM_%": 3.7486158722724864e-05}
    */

    // Network traffic of all network interfaces within the controller
    @JsonProperty("NET_out")
    long netOut;
    @JsonProperty("NET_in")
    long netIn;
    @JsonProperty("BLOCK_write") // Disk - write in Bytes
    String blockOut;
    @JsonProperty("BLOCK_read") // Disk - read in Bytes
    String blockIn;
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
    @JsonProperty("CPU_cores")
    int cpuCores;
    @JsonProperty("SYS_time")
    long sysTime;
    @JsonProperty("NET_systime")
    long netSysTime;
    @JsonProperty("CPU_used_systime")
    long cpuUsedSysTime;
    @JsonProperty("CPU_used")
    long cpuUsed;

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

    public long getNetSysTime() {
        return netSysTime;
    }

    public void setNetSysTime(long netSysTime) {
        this.netSysTime = netSysTime;
    }

    public long getCpuUsedSysTime() {
        return cpuUsedSysTime;
    }

    public void setCpuUsedSysTime(long cpuUsedSysTime) {
        this.cpuUsedSysTime = cpuUsedSysTime;
    }

    public long getCpuUsed() {
        return cpuUsed;
    }

    public void setCpuUsed(long cpuUsed) {
        this.cpuUsed = cpuUsed;
    }
}
