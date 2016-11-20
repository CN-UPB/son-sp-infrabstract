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
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MonitorManager implements Runnable {

    final static Logger logger = Logger.getLogger(MonitorManager.class);

    public static List<FunctionMonitor> monitors = new ArrayList<FunctionMonitor>();

    public static int intervalMillis = 10000;
    public static boolean active = false;

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


        asyncClient = HttpAsyncClientBuilder.create().setConnectionManager(pool).setMaxConnPerRoute(1).build();
        asyncClient.start();

        startMonitor();
    }



    public static void startMonitor(){
        if(monitorThread == null) {
            active = true;
            monitorThread = new Thread(new MonitorManager());
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

        List<MonitorHistory> historyList;

        monitoringLock.lock();

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
                    historyList = new ArrayList<MonitorHistory>();
                    for (int i=0; i<monitors.size(); i++){
                        historyList.add(monitors.get(i).history);
                    }
                }

                MessageQueue.get_deploymentQ().add(new MessageQueue.MessageQueueMonitorData(historyList));

                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        monitorThread = null;
    }




}
