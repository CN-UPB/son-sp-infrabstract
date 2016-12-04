package sonata.kernel.placement.service;

import sonata.kernel.placement.monitor.MonitorHistory;
import sonata.kernel.placement.monitor.MonitorStats;

import java.util.HashMap;
import java.util.List;

public class MonitorMessage {

    public enum SCALE_TYPE {
        SCALE_OUT,
        SCALE_IN,
        MONITOR_STATS,
        NO_SCALE
    }

    public final SCALE_TYPE type;

    public final HashMap<String, MonitorStats> stats;
    public final HashMap<String, List<MonitorStats>> stats_history;

    public MonitorMessage(SCALE_TYPE type, HashMap<String, MonitorStats> stats, HashMap<String, List<MonitorStats>> stats_history)
    {
        this.type = type;
        this.stats_history = stats_history;
        this.stats = stats;
    }



}
