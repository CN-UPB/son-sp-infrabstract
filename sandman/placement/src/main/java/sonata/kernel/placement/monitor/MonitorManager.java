package sonata.kernel.placement.monitor;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.log4j.Logger;
import sonata.kernel.placement.MessageQueue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MonitorManager implements Runnable {

    final static Logger logger = Logger.getLogger(MonitorManager.class);

    public static List<FunctionMonitor> monitors = new ArrayList<FunctionMonitor>();

    public static int intervalMillis = 10000;
    public static volatile boolean active = false;

    public static ConnectingIOReactor ioreactor;
    public static PoolingNHttpClientConnectionManager pool;
    public static CloseableHttpAsyncClient asyncClient;


    public static ReentrantLock monitoringLock = new ReentrantLock();
    public static Condition pendingRequestsCondition = monitoringLock.newCondition();
    private static int pendingRequests = 0;


    public static Thread monitorThread;

    static {
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

    public static void stopMonitor(){
        if(monitorThread != null) {
            active = false;
        }
    }

    public static void addAndStartMonitor(FunctionMonitor monitor){
        synchronized (monitors) {
            monitors.add(monitor);
        }
        startMonitor();
    }

    public static void addAndStartMonitor(List<FunctionMonitor> monitorList){
        synchronized (monitors) {
            monitors.addAll(monitorList);
        }
        startMonitor();
    }

    public static void updateMonitors(List<FunctionMonitor> addMonitors, List<FunctionMonitor> removeMonitors){
        synchronized (monitors){
            monitors.removeAll(removeMonitors);
            monitors.addAll(addMonitors);
            if(monitors.size()>pool.getDefaultMaxPerRoute())
                pool.setDefaultMaxPerRoute(monitors.size()+5);
        }
    }

    public static void stopAndRemoveAllMonitors(){
        stopMonitor();
        synchronized (monitors) {
            monitors.clear();
        }
    }

    public static void closeConnectionPool(){
        try {
            pool.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void requestFinished(){
        monitoringLock.lock();

        pendingRequests--;
        pendingRequestsCondition.signal();

        monitoringLock.unlock();
    }

    private MonitorManager(){

    }

    public void run(){

        Map<String, MonitorStats> statsMap;
        Map<String,List<MonitorStats>> statsHistoryMap;

        monitoringLock.lock();

        logger.info("Start monitoring");

        while(active){
            try {

                pendingRequests = monitors.size();

                synchronized (monitors) {
                    for (int i=0; i<monitors.size(); i++){
                        monitors.get(i).requestMonitorStats(asyncClient);
                    }
                }

                while(pendingRequests > 0) {
                    pendingRequestsCondition.await(); // TODO: timeout?
                }

                synchronized (monitors) {
                    statsHistoryMap = new HashMap<String,List<MonitorStats>>();
                    statsMap = new HashMap<String,MonitorStats>();
                    FunctionMonitor monitor;
                    List<MonitorStats> statsHistory;

                    for (int i=0; i<monitors.size(); i++){
                        monitor = monitors.get(i);
                        statsMap.put(monitor.function, monitor.statsLast);
                        statsHistory = new ArrayList<MonitorStats>();
                        statsHistory.addAll(monitor.statsList);
                        statsHistoryMap.put(monitor.function, statsHistory);
                    }
                }

                MessageQueue.get_deploymentQ().add(new MessageQueue.MessageQueueMonitorData(statsMap, statsHistoryMap));

                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        monitoringLock.unlock();

        logger.info("Stop monitoring");

        monitorThread = null;
    }




}
