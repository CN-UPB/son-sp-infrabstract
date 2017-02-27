package sonata.kernel.placement.net;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;
import sonata.kernel.placement.config.PopResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility functions to add Loadbalancing rules to a son-emu emulator using the loadbalancing REST API.
 */
public class TranslatorLoadbalancer {

    final static Logger logger = Logger.getLogger(TranslatorLoadbalancer.class);

    /**
     * Holds objects for json object
     */
    static HashMap<String,Object> lbObject;
    /**
     * Holds a list of LinkPort objects for a json object
     */
    static List<LinkPort> lbPortList;

    /**
     * Pattern to dissect the emulator response
     */
    static Pattern pattern_floatingNode = Pattern.compile("^Loadbalancer set up at ([^:]*):(.*)$");

    // initiate static objects
    static {
        lbObject = new HashMap<String,Object>();
        lbPortList = new ArrayList<LinkPort>();
        lbObject.put("dst_vnf_interfaces",lbPortList);
    }

    /**
     * Add a loadbalance rule to a son-emu emulator
     * @param balance describes the loadbalancer rule
     */
    public static void loadbalance(LinkLoadbalance balance){

        String balancePath = balance.srcPort.pop.getChainingEndpoint();
        if(!balancePath.endsWith("/"))
            balancePath += "/";
        String requestUri;

        String srcDcName = balance.srcPort.pop.getPopName();

        requestUri = balancePath+"v1/lb/"+srcDcName+"/"+balance.srcPort.stack+"/"+balance.srcPort.server+"/"+balance.srcPort.port;

        lbPortList.clear();
        lbPortList.addAll(balance.dstPorts);
        String json = null;
        try {
            json = jsonMapper.writeValueAsString(lbObject);
        } catch (JsonProcessingException e) {
            logger.error("Error when converting port list to json structure.",e);
            return;
        }

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost postRequest = new HttpPost(requestUri);
        postRequest.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        CloseableHttpResponse response = null;

        logger.info("Loadbalance "+postRequest.getRequestLine().getUri()+" "+json);

        try {
            response = client.execute(postRequest);
            if (response.getStatusLine().getStatusCode() == 500) {
                String errorMsg = null;
                if(response.getEntity()!=null && response.getEntity().getContent()!=null) {
                    IOUtils.toString(response.getEntity().getContent(), "utf-8");
                }
                logger.error("Loadbalance failed "+requestUri+" errorMsg: "+errorMsg);
            } else {
                logger.info("Loadbalance successful "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Loadbalance request aborted "+requestUri);
        }
    }

    /**
     * Adds a floating node to a son-emu emulator
     * @param balance describes the loadbalance rule
     * @return Cookie from the emulator to remove the floating node later on
     */
    public static FloatingNode floatingNode(LinkLoadbalance balance){
        String balancePath = balance.srcPort.pop.getChainingEndpoint();
        if(!balancePath.endsWith("/"))
            balancePath += "/";
        String requestUri;

        String srcDcName = balance.srcPort.pop.getPopName();

        requestUri = balancePath+"v1/lb/"+srcDcName+"/floating/"+balance.srcPort.server+"/"+balance.srcPort.port;

        lbPortList.clear();
        lbPortList.addAll(balance.dstPorts);
        String json = null;
        try {
            json = jsonMapper.writeValueAsString(lbObject);
        } catch (JsonProcessingException e) {
            logger.error("Error when converting port list to json structure.",e);
            return null;
        }

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost postRequest = new HttpPost(requestUri);
        postRequest.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        CloseableHttpResponse response = null;

        logger.info("Add floating node "+postRequest.getRequestLine().getUri()+" "+json);

        try {
            response = client.execute(postRequest);
            if (response.getStatusLine().getStatusCode() == 500) {
                String errorMsg = null;
                if(response.getEntity()!=null && response.getEntity().getContent()!=null) {
                    IOUtils.toString(response.getEntity().getContent(), "utf-8");
                }
                logger.error("Adding floating node failed "+requestUri+" errorMsg: "+errorMsg);
            } else {

                String text = IOUtils.toString(response.getEntity().getContent(), "utf-8");

                HashMap cookieMap = null;
                String cookieNr = null;
                String floatingIp = null;
                try {
                    cookieMap = jsonMapper.readValue(text, HashMap.class);
                    cookieNr = (String)cookieMap.get("cookie");
                    floatingIp = (String)cookieMap.get("floating_ip");
                    logger.info("Adding floating node successful "+requestUri+", "+text);
                    return new TranslatorLoadbalancer.FloatingNode(balance.srcPort.pop, balance.srcPort.stack, cookieNr, floatingIp, balance);

                } catch(Exception e) {
                    logger.error("Adding floating node failed "+requestUri+", call succeeded but response invalid: "+text);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Adding floating node request aborted "+requestUri);
        }
        return null;
    }

    /**
     * Removes a floating node from a son-emu emulator
     * @param floatingNode Contains the cookie for the emulator to identify the floating node
     */
    public static void unFloatingNode(FloatingNode floatingNode){
        String balancePath = floatingNode.pop.getChainingEndpoint();
        if(!balancePath.endsWith("/"))
            balancePath += "/";
        String requestUri;

        String srcDcName = floatingNode.pop.getPopName();

        requestUri = balancePath+"v1/lb/"+srcDcName+"/"+floatingNode.stackName+"/"+floatingNode.cookie+"/"+floatingNode.floatingIp;

        CloseableHttpClient client = HttpClients.createDefault();
        HttpDelete deleteRequest = new HttpDelete(requestUri);
        CloseableHttpResponse response = null;

        logger.info("Remove floating node "+deleteRequest.getRequestLine().getUri());

        try {
            response = client.execute(deleteRequest);
            if (response.getStatusLine().getStatusCode() == 500) {
                String errorMsg = null;
                if(response.getEntity()!=null && response.getEntity().getContent()!=null) {
                    IOUtils.toString(response.getEntity().getContent(), "utf-8");
                }
                logger.error("Remove floating node failed "+requestUri+" error: "+errorMsg);
            } else {
                logger.info("Remove floating node successful "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Remove floating node request aborted "+requestUri);
        }
    }

    /**
     * Removes a loadbalance rule from a son-emu emulator
     * @param srcPort describes the source port of the loadbalancer rule
     */
    public static void unloadbalance(LinkPort srcPort){
        String balancePath = srcPort.pop.getChainingEndpoint();
        if(!balancePath.endsWith("/"))
            balancePath += "/";
        String requestUri;

        String srcDcName = srcPort.pop.getPopName();

        requestUri = balancePath+"v1/lb/"+srcDcName+"/"+srcPort.stack+"/"+srcPort.server+"/"+srcPort.port;

        CloseableHttpClient client = HttpClients.createDefault();
        HttpDelete deleteRequest = new HttpDelete(requestUri);
        CloseableHttpResponse response = null;

        logger.info("Unloadbalance "+deleteRequest.getRequestLine().getUri());

        try {
            response = client.execute(deleteRequest);
            if (response.getStatusLine().getStatusCode() == 500) {
                String errorMsg = null;
                if(response.getEntity()!=null && response.getEntity().getContent()!=null) {
                    IOUtils.toString(response.getEntity().getContent(), "utf-8");
                }
                logger.error("Unloadbalance failed "+requestUri+" errorMsg: "+errorMsg);
            } else {
                logger.info("Unloadbalance successful "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Unloadbalance request aborted "+requestUri);
        }
    }

    /**
     * Describes the floating node used to connect a service to the outer world
     */
    public static class FloatingNode{

        /**
         * Loadbalancing rule to loadbalance incoming traffic between floating node and the service's input nodes
         */
        public LinkLoadbalance lbRule;
        /**
         * Datacenter the floating node is assigned to
         */
        public final PopResource pop;
        /**
         * Floating node's stack name
         */
        public final String stackName;
        /**
         * Cookie provided by the emulator
         */
        public final String cookie;
        /**
         * Emulator host IP assigned to the floating node
         */
        public final String floatingIp;

        /**
         * Creates a floating node descriptor
         * @param pop datacenter
         * @param stackName floating node's stack
         * @param cookie cookie identifying the floating node
         * @param floatingIp IP of the node
         * @param lbRule loadbalancing rule
         */
        public FloatingNode(PopResource pop, String stackName, String cookie, String floatingIp, LinkLoadbalance lbRule){
            this.pop = pop;
            this.stackName = stackName;
            this.cookie = cookie;
            this.floatingIp = floatingIp;
            this.lbRule = lbRule;
        }
    }

    /**
     * Used to map objects to json Strings
     */
    static ObjectMapper jsonMapper;

    static {
        jsonMapper = new ObjectMapper(new JsonFactory());
        jsonMapper.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        jsonMapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        jsonMapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        jsonMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Unit.class, new UnitDeserializer());
        jsonMapper.registerModule(module);
        jsonMapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
    }
}
