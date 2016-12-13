package sonata.kernel.placement.monitor;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MonitorStats {

    /* example
    {"BLOCK_write/s": 0, "CPU_cores": 4,
    "SYS_time": 1481665658589828864, "PIDS": 2,
    "CPU_%": 0.0056449892973778545, "MEM_limit": 16827117568,
    "NET_out/s": 0, "NET_in/s": 0, "MEM_used": 704512,
    "BLOCK_read/s": 0, "MEM_%": 4.1867657794212184e-05}
    */

    // Network traffic of all network interfaces within the controller
    @JsonProperty("NET_out/s")
    long netOut;
    @JsonProperty("NET_in/s")
    long netIn;
    @JsonProperty("BLOCK_write/s") // Disk - write in Bytes
    String blockOut;
    @JsonProperty("BLOCK_read/s") // Disk - read in Bytes
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
