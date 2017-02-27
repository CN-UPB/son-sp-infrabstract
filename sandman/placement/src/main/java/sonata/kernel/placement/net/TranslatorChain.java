package sonata.kernel.placement.net;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods to configure chains at a son-emu emulator using the chaining REST API.
 */
public class TranslatorChain {

    final static Logger logger = Logger.getLogger(TranslatorChain.class);

    /**
     * Adds a network chain to a son-emu emulator
     * @param chain describes the chain to be created
     */
    public static void chain(LinkChain chain){

        // Same chaining port for both datacenters
        String chainPath = chain.srcPort.pop.getChainingEndpoint();
        if(!chainPath.endsWith("/"))
            chainPath += "/";
        String requestUri;

        String srcDcName = chain.srcPort.pop.getPopName();
        String dstDcName = chain.dstPort.pop.getPopName();

        requestUri = chainPath+"v1/chain/"+srcDcName+"/"+chain.srcPort.stack+"/"+chain.srcPort.server+"/"+chain.srcPort.port+"/"
                +dstDcName+"/"+chain.dstPort.stack+"/"+chain.dstPort.server+"/"+chain.dstPort.port;

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPut putRequest = new HttpPut(requestUri);
        CloseableHttpResponse response = null;

        logger.info("Chaining "+putRequest.getRequestLine().getUri());

        try {
            response = client.execute(putRequest);
            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error("Chaining failed "+requestUri+" "+response.getStatusLine());
            } else {
                logger.info("Chaining successful "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Chaining request aborted "+requestUri);
        }
    }

    /**
     * Adds a network chain with a custom network path to a son-emu emulator
     * @param chain describes the chain to be created
     */
    public static void chainCustom(LinkChain chain){

        // Same chaining port for both datacenters
        String chainPath = chain.srcPort.pop.getChainingEndpoint();
        if(!chainPath.endsWith("/"))
            chainPath += "/";
        String requestUri;

        String srcDcName = chain.srcPort.pop.getPopName();
        String dstDcName = chain.dstPort.pop.getPopName();

        requestUri = chainPath+"v1/chain/"+srcDcName+"/"+chain.srcPort.stack+"/"+chain.srcPort.server+"/"+chain.srcPort.port+"/"
                +dstDcName+"/"+chain.dstPort.stack+"/"+chain.dstPort.server+"/"+chain.dstPort.port;

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost postRequest = new HttpPost(requestUri);
        CloseableHttpResponse response = null;

        Map<String,Object> pathMap = new HashMap<String,Object>();
        pathMap.put("path", chain.path);

        String pathString;

        try {

            pathString = mapper.writeValueAsString(pathMap);

            postRequest.setEntity(new StringEntity(pathString, ContentType.APPLICATION_JSON));

            logger.info("Chaining custom "+postRequest.getRequestLine().getUri());


            response = client.execute(postRequest);
            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error("Chaining custom failed "+requestUri);
            } else {
                logger.info("Chaining custom successful "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Chaining custom request aborted "+requestUri);
        }
    }

    /**
     * Removes a network chain from a son-emu emulator
     * @param chain
     */
    public static void unchain(LinkChain chain){

        // Same chaining port for both datacenters
        String chainPath = chain.srcPort.pop.getChainingEndpoint();
        if(!chainPath.endsWith("/"))
            chainPath += "/";
        String requestUri;

        String srcDcName = chain.srcPort.pop.getPopName();
        String dstDcName = chain.dstPort.pop.getPopName();

        requestUri = chainPath+"v1/chain/"+srcDcName+"/"+chain.srcPort.stack+"/"+chain.srcPort.server+"/"+chain.srcPort.port+"/"
                +dstDcName+"/"+chain.dstPort.stack+"/"+chain.dstPort.server+"/"+chain.dstPort.port;

        CloseableHttpClient client = HttpClients.createDefault();
        HttpDelete deleteRequest = new HttpDelete(requestUri);
        CloseableHttpResponse response = null;

        logger.info("Unchaining "+deleteRequest.getRequestLine().getUri());

        try {
            response = client.execute(deleteRequest);
            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error("Unchaining failed "+requestUri);
            } else {
                logger.info("Unchaining successful "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Unchaining request aborted "+requestUri);
        }
    }

    /**
     * Used to map objects to json Strings
     */
    protected static ObjectMapper mapper;

    static {
        ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        mapper.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        mapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Unit.class, new UnitDeserializer());
        mapper.registerModule(module);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
    }
}

