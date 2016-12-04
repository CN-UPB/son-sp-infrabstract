package sonata.kernel.placement.net;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TranslatorLoadbalancer {

    final static Logger logger = Logger.getLogger(TranslatorLoadbalancer.class);

    static HashMap<String,Object> lbObject;
    static List<LinkPort> lbPortList;

    static {
        lbObject = new HashMap<String,Object>();
        lbPortList = new ArrayList<LinkPort>();
        lbObject.put("dst_vnf_Interfaces",lbPortList);
        lbObject.put("type","select");
    }

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
                logger.error("Loadbalance failed "+requestUri);
            } else {
                logger.info("Loadbalance successful "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Loadbalance request aborted "+requestUri);
        }
    }

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
                logger.error("Unloadbalance failed "+requestUri);
            } else {
                logger.info("Unloadbalance successful "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Unloadbalance request aborted "+requestUri);
        }
    }

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
