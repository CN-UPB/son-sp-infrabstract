package sonata.kernel.placement.monitor;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.log4j.Logger;
import sonata.kernel.placement.MessageQueue;
import sonata.kernel.placement.config.PlacementConfigLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the monitoring. In particular:
 * - maintains a list of currently monitored vnfs as @FunctionMonitor objects
 * - runs a thread that regularly requests for new monitoring data
 * - sends messages with new monitoring data to the DeploymentManager
 */
public class MonitorManager implements Runnable {

    final static Logger logger = Logger.getLogger(MonitorManager.class);

    /**
     * List of current FunctionMonitors
     */
    public final static List<FunctionMonitor> monitors = new ArrayList<FunctionMonitor>();

    /**
     * Interval for monitoring requests
     */
    public static long intervalMillis = 2000;
    /**
     * Indicates to the monitoring thread to stay alive
     */
    public static volatile boolean active = false;

    /**
     * Manages the request connections
     */
    public static ConnectingIOReactor ioreactor;
    /**
     * Pool of HTTP request threads
     */
    public static PoolingNHttpClientConnectionManager pool;
    /**
     * Executes the HTTP requests
     */
    public static CloseableHttpAsyncClient asyncClient;

    /*
     * Locks the counter of completed (successful or failed) monitoring requests
     */
    public static ReentrantLock monitoringLock = new ReentrantLock();
    /**
     * Used to wake up threads waiting for the monitoringLock. This can be:
     *  - the monitoring thread when waiting for all requests to complete
     *  - threads that want to notify of completed requests
     */
    public static Condition pendingRequestsCondition = monitoringLock.newCondition();
    /**
     * Counts uncompleted requests
     */
    private static int pendingRequests = 0;

    /**
     * Monitoring thread
     */
    public static Thread monitorThread;

    static {

        intervalMillis = PlacementConfigLoader.loadPlacementConfig().getMonitorIntervalMs();

        try {
            ioreactor = new DefaultConnectingIOReactor(IOReactorConfig.custom().setIoThreadCount(4).build());
        } catch (IOReactorException e) {
            e.printStackTrace();
        }
        pool = new PoolingNHttpClientConnectionManager(ioreactor);
        pool.setDefaultMaxPerRoute(10);

        asyncClient = HttpAsyncClientBuilder.create().setConnectionManager(pool).build();
        asyncClient.start();
    }

    /**
     * Creates and starts the monitoring thread
     */
    public static void startMonitor(){
        if(monitorThread != null)
            logger.debug("MonitorThread not null!!!");
        if(monitorThread == null) {
            active = true;

            if(monitors.size()>pool.getDefaultMaxPerRoute())
                pool.setDefaultMaxPerRoute(monitors.size()+5);

            monitorThread = new Thread(new MonitorManager(), "MonitorManagerThread");
            monitorThread.start();
        }
    }

    /**
     * Stops the monitoring thread
     */
    public static void stopMonitor(){
        Thread oldThread = monitorThread;
        if(oldThread != null) {

            active = false;

            try {
                oldThread.join(15000);

            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                if(oldThread.getState() != Thread.State.TERMINATED) {
                    logger.debug("Thread is stuck, going to interrupt it.");
                    monitorThread.interrupt();
                }
            }
        }
    }

    /**
     * Adds a FunctionMonitor object and starts the monitoringThread
     * @param monitor FunctionMonitor object
     */
    public static void addAndStartMonitor(FunctionMonitor monitor){
        synchronized (monitors) {
            monitors.add(monitor);
        }
        startMonitor();
    }

    /**
     * Adds FunctionMonitor objects and starts the monitoringThread
     * @param monitorList list of FunctionMonitor objects
     */
    public static void addAndStartMonitor(List<FunctionMonitor> monitorList){
        synchronized (monitors) {
            monitors.addAll(monitorList);
        }
        startMonitor();
    }

    /**
     * Updates the list of FunctionMonitor objects by adding and removing objects
     * @param addMonitors monitors to add
     * @param removeMonitors monitors to remove
     */
    public static void updateMonitors(List<FunctionMonitor> addMonitors, List<FunctionMonitor> removeMonitors){
        synchronized (monitors){
            monitors.removeAll(removeMonitors);
            monitors.addAll(addMonitors);
            if(monitors.size()>pool.getDefaultMaxPerRoute())
                pool.setDefaultMaxPerRoute(monitors.size()+5);
        }
    }

    /**
     * Stops the monitoring thread and clears the list of FunctionMonitor objects.
     */
    public static void stopAndRemoveAllMonitors(){
        stopMonitor();
        synchronized (monitors) {
            monitors.clear();
        }
    }

    /**
     * Shuts down the connection pool.
     * Used when shutting down the application.
     */
    public static void closeConnectionPool(){
        try {
            pool.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Decreases the request counter and wakes up other waiting threads.
     * Called by FunctionMonitor objects on request completion.
     */
    public static void requestFinished(){
        monitoringLock.lock();

        pendingRequests--;
        pendingRequestsCondition.signal();

        monitoringLock.unlock();
    }

    /**
     * Creates a list of the current FunctionMonitor objects.
     * The list object is a copy of the internal list.
     * @return A list of the current FunctionMonitor objects.
     */
    public static List<FunctionMonitor> getMonitorListCopy(){
        synchronized (monitors){
            return new ArrayList<FunctionMonitor>(monitors);
        }
    }

    /**
     * Creates a monitoring message containing the lastly added monitoring data.
     * @return A monitoring message as used by the DeploymentManager
     */
    public static MessageQueue.MessageQueueMonitorData getMonitorData(){
        Map<String,List<MonitorStats>> statsHistoryMap;
        synchronized (monitors) {
            statsHistoryMap = new HashMap<String,List<MonitorStats>>();
            FunctionMonitor monitor;
            List<MonitorStats> statsHistory;

            for (int i=0; i<monitors.size(); i++){
                monitor = monitors.get(i);
                statsHistory = new ArrayList<MonitorStats>();
                statsHistory.addAll(monitor.statsList);
                statsHistoryMap.put(monitor.function, statsHistory);
            }
        }
        return new MessageQueue.MessageQueueMonitorData(statsHistoryMap);
    }

    private MonitorManager(){
    }

    /**
     * Monitoring thread loop.
     * Loop:
     * - starts request for all FunctionMonitor objects
     * - waits for request completion
     * - sends a monitoring message to the DeploymentManager
     * - waits a certain interval
     * The loop loops while it is @active.
     */
    public void run(){

        monitoringLock.lock();

        logger.info("Start monitoring");

        while(active){
            try {

                pendingRequests = monitors.size();

                monitoringLock.unlock();

                synchronized (monitors) {
                    for (int i=0; i<monitors.size(); i++){
                        monitors.get(i).requestMonitorStats(asyncClient);
                    }
                }

                monitoringLock.lock();

                while(pendingRequests > 0) {
                    pendingRequestsCondition.await(); // TODO: timeout?
                }

                MessageQueue.get_deploymentQ().add(getMonitorData());

                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
                logger.error(e);
                logger.error(e);
                for(int i=0; i<monitors.size(); i++) {
                    FunctionMonitor monitor = monitors.get(i);
                    if(monitor!=null)
                        monitor.stopMonitorRequest();
                }
            }
        }

        monitoringLock.unlock();

        logger.info("Stop monitoring");

        monitorThread = null;
    }
}