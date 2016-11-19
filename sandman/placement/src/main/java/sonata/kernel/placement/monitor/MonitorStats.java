package sonata.kernel.placement.monitor;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MonitorStats {

    /* current
    {"NET_out": "648 B", "MEM_used": "1.07 MiB", "PIDS": "3", "NET_in": "1.596 kB",
    "MEM_limit": "15.67 GiB", "CPU": "0.00%", "MEM_%": "0.01%"}
    */
    /* experimental
    {'NET_out': [], 'CPU_%': 0.0, 'MEM_used': 1101824, 'PIDS': 4,
    'NET_in': ['Total', '0'], 'SYS_time': 1479224676000000000L, 'MEM_limit': 16827117568, 'MEM_%': 6.54790694572272e-05}
    */
    /* new
    {'MEM_used': 790528, 'PIDS': 2, 'BLOCK_out': [], 'NET_out': 648,
     'SYS_time': 1479522691737230080L, 'MEM_limit': 16827117568,
     'NET_in': 648, 'BLOCK_in': ['Total', '0'], 'MEM_%': 4.6979406710947397e-05, 'CPU_%': 0.0}
    */

    @JsonProperty("NET_out")
    String netOut;
    @JsonProperty("NET_in")
    String netIn;
    @JsonProperty("MEM_used")
    String memoryUsed;
    @JsonProperty("PIDS")
    String processIds;
    @JsonProperty("MEM_limit")
    String memoryLimit;
    @JsonProperty("CPU")
    String cpu;
    @JsonProperty("MEM_%")
    String memoryPercentage;

    public String getNetOut() {
        return netOut;
    }

    public void setNetOut(String netOut) {
        this.netOut = netOut;
    }

    public String getNetIn() {
        return netIn;
    }

    public void setNetIn(String netIn) {
        this.netIn = netIn;
    }

    public String getMemoryUsed() {
        return memoryUsed;
    }

    public void setMemoryUsed(String memoryUsed) {
        this.memoryUsed = memoryUsed;
    }

    public String getProcessIds() {
        return processIds;
    }

    public void setProcessIds(String processIds) {
        this.processIds = processIds;
    }

    public String getMemoryLimit() {
        return memoryLimit;
    }

    public void setMemoryLimit(String memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    public String getCpu() {
        return cpu;
    }

    public void setCpu(String cpu) {
        this.cpu = cpu;
    }

    public String getMemoryPercentage() {
        return memoryPercentage;
    }

    public void setMemoryPercentage(String memoryPercentage) {
        this.memoryPercentage = memoryPercentage;
    }
}
