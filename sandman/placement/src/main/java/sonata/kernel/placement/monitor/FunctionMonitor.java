package sonata.kernel.placement.monitor;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;
import sonata.kernel.placement.PlacementConfigLoader;
import sonata.kernel.placement.config.PopResource;
import sonata.kernel.placement.service.FunctionInstance;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Requests and saves monitor information for one vnf.
 * The FutureCallback interface contains callback methods for the monitoring data response.
 */
public class FunctionMonitor implements FutureCallback<HttpResponse> {

    final static Logger logger = Logger.getLogger(FunctionMonitor.class);

    /**
     * Datacenter the vnf belongs to
     */
    public PopResource dc;
    /**
     * Name of the stack the vnf belongs to
     */
    public String stack;
    /**
     * Name of the server
     */
    public String function;
    /**
     * HTTP-URL used to request monitor data
     */
    public String requestPath;
    /**
     * Vnf's instance object
     */
    public FunctionInstance instance;

    /**
     * HTTP request object contains the request details
     */
    public HttpGet httpRequest;
    /**
     * Response object of the last request
     */
    public Future<HttpResponse> lastRequest;

    /**
     * List of monitoring data
     */
    public List<MonitorStats> statsList;
    /**
     * Last monitoring data received
     */
    public MonitorStats statsLast;
    /**
     * Limit of monitoring data stored in statsList
     */
    public long historyLimit = PlacementConfigLoader.loadPlacementConfig().getMonitorHistoryLimit();

    /**
     * Creates a new FunctionMonitor object
     * @param datacenter  Vnf's datacenter
     * @param stack Vnf's stack
     * @param function Vnf's server name
     */
    public FunctionMonitor(PopResource datacenter, String stack, String function){
        this.dc = datacenter;
        this.stack = stack;
        this.function = function;
        this.requestPath = datacenter.getMonitoringEndpoint()+"v1/monitor/"+dc.getPopName()+"/"+stack+"/"+function;
        httpRequest = new HttpGet(this.requestPath);
        statsList = new ArrayList<MonitorStats>();
    }

    /**
     * Stops last request if it's not completed yet
     */
    public void stopMonitorRequest() {
        if(lastRequest != null)
            lastRequest.cancel(true);
    }

    /**
     * Executes a new request for monitoring data.
     * The request does not block.
     * @param asyncClient HTTP client that executes the request
     */
    public void requestMonitorStats(CloseableHttpAsyncClient asyncClient){
        logger.debug("Requested monitor status for "+dc.getPopName()+"_"+stack+"_"+function);
        lastRequest = asyncClient.execute(this.httpRequest, this);
        logger.debug("Done Requested monitor status for "+dc.getPopName()+"_"+stack+"_"+function);
    }

    /**
     * Called when the request was completed successfully.
     * Maps the response data to a MonitorStats object and notifies MonitorManager.
     * @param response Contains the response data for the monitoring data request
     */
    @Override
    public void completed(HttpResponse response) {

        String json = null;
        MonitorStats stats = null;
        try {
            json = IOUtils.toString(response.getEntity().getContent(), "utf-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        //logger.debug(json);
        stats = readJsonMonitoring(json);

        if(stats != null) {

            statsLast = stats;
            statsList.add(stats);
            if(statsList.size()>historyLimit)
                statsList.remove(0);
        }

        MonitorManager.requestFinished();

        lastRequest = null;
        logger.debug("Incoming monitor status for "+dc.getPopName()+"_"+stack+"_"+function+": "+new String(json));
    }

    /**
     * Called when the request fails
     * @param e
     */
    @Override
    public void failed(Exception e) {
        MonitorManager.requestFinished();
        lastRequest = null;
        logger.debug("Monitor request failed for "+dc.getPopName()+"_"+stack+"_"+function);
    }

    /**
     * Called when the request was cancelled
     */
    @Override
    public void cancelled() {
        MonitorManager.requestFinished();
        lastRequest = null;
        logger.debug("Monitor request cancelled for "+dc.getPopName()+"_"+stack+"_"+function);
    }

    /**
     * Maps a JSON String to a MonitorStats object.
     * @param text JSON String
     * @return MonitorStats object if the text contained valid monitoring data, else null
     */
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
     * Compares this object to another FunctionMonitor objects
     * @param obj Other FunctionMonitor object
     * @return true if the objects concern the same vnf, else false
     */
    public boolean equals(Object obj){
        if(obj == null || ! (obj instanceof FunctionMonitor))
            return false;
        FunctionMonitor monitor = (FunctionMonitor)obj;
        if(dc.getPopName().equals(monitor.dc.getPopName()) &&
           stack.equals(monitor.stack) &&
           function.equals(monitor.function))
            return true;
        return false;
    }
}
