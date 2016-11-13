package sonata.kernel.placement.monitor;

import java.util.ArrayList;
import java.util.List;

public class MonitorManager {

    public static List<FunctionMonitor> monitors = new ArrayList<FunctionMonitor>();

    public static void addAndStartMonitor(FunctionMonitor monitor){
        monitors.add(monitor);
        monitor.startMonitor();
    }

    public static void stopAndRemoveAllMonitors(){
        while(!monitors.isEmpty()) {
            FunctionMonitor monitor = monitors.remove(0);
            monitor.stopMonitor();
        }
    }

}
