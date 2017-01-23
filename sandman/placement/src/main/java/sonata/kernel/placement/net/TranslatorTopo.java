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
import sonata.kernel.placement.service.NetworkTopologyGraph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class TranslatorTopo {

    final static Logger logger = Logger.getLogger(TranslatorTopo.class);

    public static NetworkTopologyGraph.NetworkTopology_J get_topology(String topoEndpoint){
        logger.debug("TranslatorTopo::get_topology ENTRY");

        String topoPath = topoEndpoint;
        if(!topoPath.endsWith("/"))
            topoPath += "/";
        String requestUri;
        String json = null;
        NetworkTopologyGraph.NetworkTopology_J topology;

        requestUri = topoPath+"v1/topo";

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

                topology = read_json_topology(json);
                logger.debug("TranslatorTopo::get_topology EXIT");
                return topology;
            } else {
                logger.error("Topology failed "+requestUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Topology request aborted "+requestUri);
        }
        logger.debug("TranslatorTopo::get_topology EXIT");
        return null;
    }

    public static NetworkTopologyGraph.NetworkTopology_J read_json_topology(String text){
        logger.debug("TranslatorTopo::readJsonTopology ENTRY");
/**
        System.out.println(new File(".").getAbsolutePath());
        String text1=null;
        try {
            FileInputStream fin= new FileInputStream(new File("topo.txt"));

            text1 = IOUtils.toString(fin, "utf-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
**/
        ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        SimpleModule module = new SimpleModule();

        module.addDeserializer(Unit.class, new UnitDeserializer());
        mapper.registerModule(module);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        NetworkTopologyGraph.NetworkTopology_J topo = null;
        try {
            topo = mapper.readValue(text, NetworkTopologyGraph.NetworkTopology_J.class);
        } catch (IOException e) {
            logger.debug("Topology JSON parsing failure",e);
        }
        logger.debug("TranslatorTopo::readJsonTopology EXIT");
        return topo;
    }
}
