package sonata.kernel.placement.net;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import sonata.kernel.placement.config.PopResource;

import java.io.IOException;

public class TranslatorChain {

    final static Logger logger = Logger.getLogger(TranslatorChain.class);

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
            if (response.getStatusLine().getStatusCode() == 500) {
                logger.error("Chaining failed "+requestUri);
            } else {
                logger.info("Chaining successful "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Chaining request aborted "+requestUri);
        }
    }

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
            if (response.getStatusLine().getStatusCode() == 500) {
                logger.error("Unchaining failed "+requestUri);
            } else {
                logger.info("Unchaining successful "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Unchaining request aborted "+requestUri);
        }
    }

}

