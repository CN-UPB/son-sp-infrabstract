package sonata.kernel.placement.monitor;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;
import sonata.kernel.placement.config.PopResource;

import java.io.IOException;
import java.util.Arrays;

/**
 * Controls a monitor thread to monitor one function
 */
public class FunctionMonitor {

    final static Logger logger = Logger.getLogger(FunctionMonitor.class);

    public PopResource dc;
    public String stack;
    public String function;
    public String requestPath;

    public long interval; // in milliseconds
    public Thread monitorThread;
    public MonitorInstance monitorInstance;
    public CloseableHttpClient httpClient = null;
    public HttpGet httpRequest;

    public int historySize = 100;
    // resources: cpu in s, memory in bytes, network io?, disk io?
    // TODO: cumulative values?
    // historySize x {system time in nanoseconds, value}
    ValueHistory cpuHistory;
    ValueHistory memoryHistory;

    public FunctionMonitor(PopResource datacenter, String stack, String function, long interval){
        this.dc = datacenter;
        this.stack = stack;
        this.function = function;
        this.interval = interval;
        this.monitorInstance = new MonitorInstance();
        this.monitorThread = new Thread(this.monitorInstance);
        this.requestPath = datacenter.getMonitoringEndpoint()+"v1/monitor/"+function;
        httpRequest = new HttpGet(this.requestPath);
        cpuHistory = new ValueHistory(historySize);
        memoryHistory = new ValueHistory(historySize);
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

                if(httpClient == null)
                    httpClient = HttpClients.createDefault();

                CloseableHttpResponse response = null;
                try {
                    response = httpClient.execute(httpRequest);
                    String json = IOUtils.toString(response.getEntity().getContent(), "utf-8");
                    readJsonMonitoring(json);

                    cpuHistory.addValue(0,0);
                    memoryHistory.addValue(0,0);

                    // TODO: Create monitor message if necessary
                    // TODO: Maybe check some limits

                } catch (IOException e) {
                    e.printStackTrace();
                    // TODO: Add fallback values?
                }
            }
        }
    }

    public Object readJsonMonitoring(String text){
        ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        SimpleModule module = new SimpleModule();

        module.addDeserializer(Unit.class, new UnitDeserializer());
        mapper.registerModule(module);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        Object json = null;
        try {
            // TODO: Add Monitoring data structure
            json = mapper.readValue(text, Object.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return json;
    }

    /**
     * Ringbuffer like array structure
     */
    public static class ValueHistory{

        public long[][] data;
        public int nextIndex = 0;

        public ValueHistory(int length){
            data = new long[length][2];
        }

        public void addValue(long time, long value) {
            data[nextIndex][0] = time;
            data[nextIndex][1] = value;
            nextIndex++;
            if(nextIndex>=data.length)
                nextIndex = 0;
        }

        public long[][] getLast(int number){
            int newLength = Math.min(number, data.length);
            if(nextIndex >= newLength) {
                // copy in one go
                return Arrays.copyOfRange(data, nextIndex-newLength, nextIndex);
            } else {
                long[][] newData = new long[newLength][2];
                // two copies since range is split
                System.arraycopy(data, 0, newData, newLength-data.length, nextIndex);
                System.arraycopy(data, data.length-(newLength-nextIndex), newData, 0, newLength-nextIndex);
                return newData;
            }
        }

        public int length(){
            return data.length;
        }
    }

}
