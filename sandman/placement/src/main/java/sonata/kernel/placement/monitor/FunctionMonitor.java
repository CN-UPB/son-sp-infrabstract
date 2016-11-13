package sonata.kernel.placement.monitor;

import org.apache.log4j.Logger;
import sonata.kernel.placement.config.PopResource;

/**
 * Controls a monitor thread to monitor one function
 */
public class FunctionMonitor {

    final static Logger logger = Logger.getLogger(FunctionMonitor.class);

    public PopResource dc;
    public String stack;
    public String function;

    public long interval; // in milliseconds
    public Thread monitorThread;
    public MonitorInstance monitorInstance;

    public FunctionMonitor(PopResource datacenter, String stack, String function, long interval){
        this.dc = datacenter;
        this.stack = stack;
        this.function = function;
        this.interval = interval;
        this.monitorInstance = new MonitorInstance();
        this.monitorThread = new Thread(this.monitorInstance);
    }

    public void startMonitor(){
        this.monitorThread.start();
    }

    public void stopMonitor(){
        this.monitorThread.interrupt();
    }

    class MonitorInstance implements Runnable{

        public boolean stopMonitoring = false;

        public void run(){

            while(true) {

                // Sleep until next status request
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if(stopMonitoring)
                    return;

                // TODO: Execute monitoring request
                // TODO: Keep connection open if possible, check for connection status
                // TODO: Add gather status values
                // TODO: Create monitor message if necessary
                // TODO: Maybe check some limits


            }
        }
    }


}
