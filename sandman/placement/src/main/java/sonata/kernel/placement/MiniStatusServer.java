package sonata.kernel.placement;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import fi.iki.elonen.NanoHTTPD;
import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

public class MiniStatusServer {

    final static Logger logger = Logger.getLogger(MiniStatusServer.class);

    public static Path rootPath = Paths.get("sandman","placement","YAML","static");
    public static String root = rootPath.toString();
    public static File rootFile = rootPath.toFile();

    public static Map<String,String> extMimeMap = new HashMap<String,String>();

    static {
        if (!rootFile.exists())
            logger.error("Path does not exist "+root);
        extMimeMap.put("json","application/json");
        extMimeMap.put("js","text/javascript");
        extMimeMap.put("css","text/css");
        extMimeMap.put("htm","text/html");
        extMimeMap.put("html","text/html");

    }

    public static NanoHTTPD.Response serveDynamic(NanoHTTPD.IHTTPSession session){
        String uri = session.getUri();
        String json = null;
        Object jsonObj = null;


        if(uri.startsWith("/status"))
            uri = uri.substring(7);

        if(uri.startsWith("/status")) {

            Map<String,Object> statusObj = new HashMap<String, Object>();

            if(DeploymentManager.currentInstance == null) {
                statusObj.put("status", "UNDEPLOYED");
            } else {
                statusObj.put("status", "DEPLOYED");
            }
            statusObj.put("packageCount",Catalogue.packages.size());
            jsonObj = statusObj;
        }

        try {
            if(jsonObj != null) {
                json = jsonMapper.writeValueAsString(jsonObj);
                logger.debug("200 - "+uri);
                return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", json);
            }
        } catch (JsonProcessingException e) {
        }

        logger.debug("404 - "+uri);
        return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, null, null);
    }

    public static NanoHTTPD.Response serveStatic(NanoHTTPD.IHTTPSession session) {
        String uri = session.getUri();

        if(uri.startsWith("/static"))
            uri = uri.substring(7);

        File requestFile = searchForFile(uri);
        if (requestFile != null) {

            String[] fileParts = requestFile.getName().split("\\.");
            String ext = fileParts.length>0 ? fileParts[fileParts.length-1] : "";
            String mime = extMimeMap.get(ext);
            if (mime == null)
                mime = "pext/plain";

            FileInputStream fis = null;
            try {
                fis = new FileInputStream(requestFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                logger.error(e);
            }
            if (fis != null) {
                logger.debug("200 - "+uri);
                return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mime, fis, requestFile.length());
            }
        }

        logger.debug("404 - "+uri);
        return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, null, null);
    }

    public static File searchForFile(String file){
        File requestFile;
        File rootDirectory;
        try {
            requestFile = Paths.get(root, file).toFile().getCanonicalFile();
            rootDirectory = rootFile.getCanonicalFile();
            //logger.debug("Request "+requestFile);
            //logger.debug("Root "+rootDirectory);
            if (requestFile.exists() && requestFile.getParentFile().equals(rootDirectory)) {
                return requestFile;
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error(e);
        }
        return null;
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
