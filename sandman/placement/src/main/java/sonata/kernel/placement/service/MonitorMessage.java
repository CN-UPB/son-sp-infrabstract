package sonata.kernel.placement.service;

import sonata.kernel.placement.monitor.MonitorStats;

import java.util.List;
import java.util.Map;

public class MonitorMessage {

    public enum SCALE_TYPE {
        SCALE_OUT,
        SCALE_IN,
        MONITOR_STATS,
        NO_SCALE
    }

    public SCALE_TYPE type;

    public final Map<String, List<MonitorStats>> stats_history;

    public MonitorMessage(SCALE_TYPE type, Map<String, List<MonitorStats>> stats_history)
    {
        this.type = type;
        this.stats_history = stats_history;
    }



}
