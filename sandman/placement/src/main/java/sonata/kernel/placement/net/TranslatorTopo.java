package sonata.kernel.placement.net;

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

import java.io.IOException;

public class TranslatorTopo {

    final static Logger logger = Logger.getLogger(TranslatorTopo.class);

    public static NetworkTopology topo(String topoEndpoint){

        // Same chaining port for both datacenters
        String topoPath = topoEndpoint;
        if(!topoPath.endsWith("/"))
            topoPath += "/";
        String requestUri;
        String json = null;
        NetworkTopology topology;

        requestUri = topoPath+"v1/topo/";

        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet getRequest = new HttpGet(requestUri);
        CloseableHttpResponse response = null;

        logger.info("Topology "+getRequest.getRequestLine().getUri());

        try {
            response = client.execute(getRequest);

            if (response.getStatusLine().getStatusCode() == 200) {
                logger.info("Topology successful "+requestUri);
                try {
                    json = IOUtils.toString(response.getEntity().getContent(), "utf-8");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                topology = readJsonTopology(json);
                return topology;
            } else {
                logger.error("Topology failed "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Topology request aborted "+requestUri);
        }
        return null;
    }

    public static NetworkTopology readJsonTopology(String text){
        ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        SimpleModule module = new SimpleModule();

        module.addDeserializer(Unit.class, new UnitDeserializer());
        mapper.registerModule(module);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        NetworkTopology topo = null;
        try {
            topo = mapper.readValue(text, NetworkTopology.class);
        } catch (IOException e) {
            logger.debug("Topology JSON parsing failure",e);
        }
        return topo;
    }
}
