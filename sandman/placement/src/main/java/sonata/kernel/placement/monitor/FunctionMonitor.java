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

    public MonitorHistory history;
    public int historySize = 100;

    public FunctionMonitor(PopResource datacenter, String stack, String function){
        this.dc = datacenter;
        this.stack = stack;
        this.function = function;
        this.requestPath = datacenter.getMonitoringEndpoint()+"v1/monitor/"+dc.getPopName()+"/"+stack+"/"+function;
        httpRequest = new HttpGet(this.requestPath);
        history = new MonitorHistory(datacenter.getPopName(), stack, function, historySize);
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

            history.cpuHistory.addValue(stats.getSysTime(),stats.getCpu());
            history.memoryHistory.addValue(stats.getSysTime(), stats.getMemoryUsed());
        }

        MonitorManager.requestFinished();

        // TODO: Create monitor message if necessary
        // TODO: Maybe check some limits

        logger.debug("Incoming monitor status for "+dc.getPopName()+"_"+stack+"_"+function+": "+new String(json));
    }

    @Override
    public void failed(Exception e) {
        MonitorManager.requestFinished();
        logger.debug("Monitor request failed for "+dc.getPopName()+"_"+stack+"_"+function);
    }

    @Override
    public void cancelled() {
        MonitorManager.requestFinished();
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


}
