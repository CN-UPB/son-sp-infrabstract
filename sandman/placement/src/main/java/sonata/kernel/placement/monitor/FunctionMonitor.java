package sonata.kernel.placement.monitor;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;
import sonata.kernel.placement.config.PopResource;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Future;

/**
 * Requests and saves monitor information for one function
 */
public class FunctionMonitor implements FutureCallback<HttpResponse> {

    final static Logger logger = Logger.getLogger(FunctionMonitor.class);

    public PopResource dc;
    public String stack;
    public String function;
    public String requestPath;

    public CloseableHttpClient httpClient = null;
    public HttpGet httpRequest;

    public int historySize = 100;
    ValueHistoryDouble cpuHistory;
    ValueHistoryLong memoryHistory;

    public FunctionMonitor(PopResource datacenter, String stack, String function){
        this.dc = datacenter;
        this.stack = stack;
        this.function = function;
        //this.requestPath = datacenter.getMonitoringEndpoint()+"v1/monitor/"+function;
        this.requestPath = datacenter.getMonitoringEndpoint()+"v1/monitor/"+dc.getPopName()+"/"+stack+"/"+function;
        httpRequest = new HttpGet(this.requestPath);
        cpuHistory = new ValueHistoryDouble(historySize);
        memoryHistory = new ValueHistoryLong(historySize);
    }

    public void requestMonitorStats(CloseableHttpAsyncClient asyncClient){
        logger.debug("Requested monitor status for "+dc.getPopName()+"_"+stack+"_"+function);
        Future<HttpResponse> f = asyncClient.execute(this.httpRequest, this);
    }

    @Override
    public void completed(HttpResponse response) {

        String json = null;
        MonitorStats stats = null;
        try {
            json = IOUtils.toString(response.getEntity().getContent(), "utf-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        stats = readJsonMonitoring(json);

        if(stats != null) {
            cpuHistory.addValue(stats.getSysTime(),stats.getCpu());
            memoryHistory.addValue(stats.getSysTime(), stats.getMemoryUsed());
        }

        // TODO: Create monitor message if necessary
        // TODO: Maybe check some limits

        logger.debug("Incoming monitor status for "+dc.getPopName()+"_"+stack+"_"+function+": "+new String(json));
    }

    @Override
    public void failed(Exception e) {
        logger.debug("Monitor request failed for "+dc.getPopName()+"_"+stack+"_"+function);
    }

    @Override
    public void cancelled() {
        logger.debug("Monitor request cancelled for "+dc.getPopName()+"_"+stack+"_"+function);
    }

    public MonitorStats readJsonMonitoring(String text){
        ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        SimpleModule module = new SimpleModule();

        module.addDeserializer(Unit.class, new UnitDeserializer());
        mapper.registerModule(module);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        MonitorStats stats = null;
        try {
            stats = mapper.readValue(text, MonitorStats.class);
        } catch (IOException e) {
            logger.debug("MonitorStats JSON parsing failure",e);
        }
        return stats;
    }

    /**
     * Ringbuffer like array structure
     */
    public static class ValueHistoryLong{

        public long[] time;
        public long[] data;
        public int nextIndex = 0;

        public ValueHistoryLong(int length){
            data = new long[length];
            time = new long[length];
        }

        public void addValue(long time, long value) {
            this.time[nextIndex] = time;
            this.data[nextIndex] = value;
            nextIndex++;
            if(nextIndex>=data.length)
                nextIndex = 0;
        }

        public int getLast(int number, long[] time, long[] data){
            int newLength = Math.min(number, data.length);
            if(nextIndex >= newLength) {
                // copy in one go
                System.arraycopy(this.data, newLength-data.length, data, 0, newLength);
                System.arraycopy(this.time, newLength-data.length, time, 0, newLength);
            } else {
                // two copies since range is split
                System.arraycopy(this.data, 0, data, newLength-nextIndex, nextIndex);
                System.arraycopy(this.time, 0, time, newLength-nextIndex, nextIndex);

                System.arraycopy(this.data, this.data.length-(newLength-nextIndex), data, 0, newLength-nextIndex);
                System.arraycopy(this.time, this.time.length-(newLength-nextIndex), time, 0, newLength-nextIndex);
            }
            return newLength;
        }

        public int length(){
            return data.length;
        }
    }

    public static class ValueHistoryDouble{

        public long[] time;
        public double[] data;
        public int nextIndex = 0;

        public ValueHistoryDouble(int length){
            time = new long[length];
            data = new double[length];
        }

        public void addValue(long time, double value) {
            this.time[nextIndex] = time;
            this.data[nextIndex] = value;
            nextIndex++;
            if(nextIndex>=data.length)
                nextIndex = 0;
        }

        public int getLast(int number, long[] time, double[] data){
            int newLength = Math.min(number, data.length);
            if(nextIndex >= newLength) {
                // copy in one go
                System.arraycopy(this.data, newLength-data.length, data, 0, newLength);
                System.arraycopy(this.time, newLength-data.length, time, 0, newLength);
            } else {
                // two copies since range is split
                System.arraycopy(this.data, 0, data, newLength-nextIndex, nextIndex);
                System.arraycopy(this.time, 0, time, newLength-nextIndex, nextIndex);

                System.arraycopy(this.data, this.data.length-(newLength-nextIndex), data, 0, newLength-nextIndex);
                System.arraycopy(this.time, this.time.length-(newLength-nextIndex), time, 0, newLength-nextIndex);
            }
            return newLength;
        }

        public int length(){
            return data.length;
        }
    }
}
