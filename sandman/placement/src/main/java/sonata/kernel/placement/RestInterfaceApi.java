package sonata.kernel.placement;

//import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONWriter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import sonata.kernel.VimAdaptor.commons.nsd.ServiceDescriptor;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;
import sonata.kernel.placement.pd.SonataPackage;

class RestInterfaceServerApi extends NanoHTTPD implements Runnable {
    final Logger logger = Logger.getLogger(RestInterfaceServerApi.class);

    public RestInterfaceServerApi() {
        super(8080);
    }

    public RestInterfaceServerApi(String hostname, int port) throws IOException {
        super(hostname, port);
        logger.info("Started RESTful server Hostname: "
                + hostname + " Port: " + port);
    }

    public void start_server() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    public void run() {
        try {
            start_server();
        } catch (IOException ioe) {
            logger.error("Failed to start server ",ioe);
        }
    }
    public static String extractNumber(String str) {                

        if(str == null || str.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for(char c : str.toCharArray()){
            if(Character.isDigit(c)){
                sb.append(c);
                found = true;
            }
        }
        if (found == false) {
        	return "";
        }

        return sb.toString();
    }

    @Override
    public Response serve(IHTTPSession session) {
        final Logger logger = Logger.getLogger(RestInterfaceServerApi.class);
        try {
            String uri = session.getUri();
            String req_index = extractNumber(uri);
            String req_uri = uri;

            if(uri.startsWith("/status")) {
                return MiniStatusServer.serveDynamic(session);
            }
            else
            if(uri.startsWith("/static")) {
                return MiniStatusServer.serveStatic(session);
            }
            else
            if("/packages".equals(uri) && session.getMethod().equals(Method.POST)) {
                logger.info("Package post");
                session.getParms();
                Integer contentLength = Integer.parseInt(session.getHeaders().get("content-length"));
                logger.debug("Content Length is " + contentLength);
                byte[] buffer = new byte[contentLength];
                int alreadyRead = 0;
                int read = -1;
                while(alreadyRead < contentLength) {
                    read = session.getInputStream().read(buffer, alreadyRead, contentLength-alreadyRead);
                    if(read > 0)
                        alreadyRead += read;
                    if(read == -1)
                        break;
                }
                if(alreadyRead < contentLength) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, null, "Please try again, but next time a little bit slower.");
                }

                List<MultiPartFormDataPart> parts = parseMultiPartFormData(session, buffer);
                if(parts.size()==1) {
                    buffer = parts.get(0).data;
                } else {
                    // Fallback if above code fails
                    buffer = stripMultiPartFormDataHeader(session, buffer);
                }

                String base_dir = PackageLoader.processZipFile(buffer);

                String jsonPackage = "OK";
                SonataPackage pack = PackageLoader.zipByteArrayToSonataPackage(buffer);

                if(pack == null)
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, null, null);

                int newIndex = Catalogue.addPackage(pack);
                jsonPackage = Catalogue.getJsonPackageDescriptor(newIndex);
                JSONObject jsonObj = new JSONObject();
                jsonObj.put("service_uuid", newIndex);
                String ret_index = jsonObj.toString();
                logger.info("Index Returned is "+ret_index);
                logger.info("Json Package is "+jsonPackage);

                return newFixedLengthResponse(Response.Status.CREATED, "application/json", ret_index);
            }
            else
            if("/packages".equals(uri) && session.getMethod().equals(Method.GET)) {
				logger.info("Package list");
                String jsonPackageList = Catalogue.getJsonPackageList();
                String ret_index=null;
                JSONObject jsonObj = new JSONObject();
            	JSONArray jsonArray = new JSONArray();
                for (int i = 0;i < Catalogue.packages.size();i++){
                	jsonArray.put(i);
                }
                jsonObj.put("service_uuid_list", jsonArray);
                ret_index = jsonObj.toString();
                System.out.println("Json list: "+ret_index);
                if(jsonPackageList == null)
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, null, null);
                else
                	return newFixedLengthResponse(Response.Status.OK, "application/json", ret_index);
            }
            else
            if("/undeploy".equals(uri) && session.getMethod().equals(Method.GET)) {
                logger.info("Undeploy");

                MessageQueue.MessageQueueUnDeployData message = new MessageQueue.MessageQueueUnDeployData();
                MessageQueue.get_deploymentQ().put(message);
                synchronized(message) {
                    message.wait(10000);
                }

                if(message.responseId == -1) {
                    logger.debug("Undeployment timed out.");
                    return newFixedLengthResponse(new Status(504, "Undeployment is pending"), null, null);
                }
                else
                    return newFixedLengthResponse(new Status(message.responseId, message.responseMessage), null, null);
            }
            else
            if(req_uri.equals(uri) && session.getMethod().equals(Method.POST)) {
                logger.info("Deploy");
            	int newIndex;
               	String bla = null;
            	ObjectMapper mapper = getJSONMapper();
            	Integer contentLength = Integer.parseInt(session.getHeaders().get("content-length"));
            	byte[] buffer = new byte[contentLength];
            	session.getInputStream().read(buffer, 0, contentLength);
            	String str = new String(buffer);
            	String index = extractNumber(str);
            	/*try{
            		map = mapper.readValue(bla, HashMap.class);
            	} catch(IOException e)
            	{
            		// return error
            	}*/
               //map.get("service_uuid"));
            	if (index.length() > 0) {
                	newIndex = Integer.parseInt(index);
                	try {

                        MessageQueue.MessageQueueDeployData message = new MessageQueue.MessageQueueDeployData(newIndex);
                        MessageQueue.get_deploymentQ().put(message);
                        synchronized(message) {
                            message.wait(10000);
                        }

                        String packageJson = Catalogue.getJsonPackageDescriptor(newIndex);

                        if(message.responseId == -1) {
                            logger.debug("Deployment timed out.");
                            return newFixedLengthResponse(new Status(504, "Deployment is pending"), "application/json", packageJson);
                        }
                        else

                            return newFixedLengthResponse(new Status(message.responseId, message.responseMessage), "application/json", packageJson);

                	}
                	catch(Exception e){
                		e.printStackTrace();
                	}	
                }
            	else {
            		return newFixedLengthResponse(Response.Status.NOT_IMPLEMENTED, null, null);
            	}
            }
            else
                return newFixedLengthResponse(Response.Status.NOT_IMPLEMENTED, null, null);

        } catch (IOException e) {

        } catch (Exception e) {
            e.printStackTrace();
        }

        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, null, null);
    }
    
    public static ObjectMapper getJSONMapper(){
    	ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Unit.class, new UnitDeserializer());
        mapper.registerModule(module);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        return mapper;
    }

    public static byte[] stripMultiPartFormDataHeader(IHTTPSession session, byte[] buffer) {
        // Check if POST request contains multipart/form-data
        if (session.getMethod().compareTo(Method.POST) == 0 && session.getHeaders().containsKey("content-type") &&
                session.getHeaders().get("content-type").startsWith("multipart/form-data")) {

            // Assume UTF-8 encoding
            CharsetDecoder dec = Charset.forName("UTF-8").newDecoder();

            // Create comparison charbuffers
            CharBuffer nlnl = CharBuffer.wrap("\n\n");
            CharBuffer crnlcrnl = CharBuffer.wrap("\r\n\r\n");
            int formDataBorder = -1;
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

            // Find border for first multipart/form-data boundary
            // Assume there is only one part
            for (int i = 0; i < buffer.length; i++) {

                byteBuffer.position(0);
                byteBuffer.limit(i);

                try {
                    CharBuffer charBuffer = dec.decode(byteBuffer);
                    // Check if end of sequence is "\n\n" or "\r\n\r\n"
                    if ((charBuffer.length() > 1 && charBuffer.subSequence(charBuffer.length() - 2, charBuffer.length()).compareTo(nlnl) == 0) ||
                            (charBuffer.length() > 3 && charBuffer.subSequence(charBuffer.length() - 4, charBuffer.length()).compareTo(crnlcrnl) == 0)) {
                        formDataBorder = i;
                        break;
                    }
                } catch (CharacterCodingException e) {
                    return buffer;
                }

            }
            if (formDataBorder != -1)
                return Arrays.copyOfRange(buffer, formDataBorder, buffer.length);
            else
                return buffer;
        } else
            return buffer;
    }

    public static List<MultiPartFormDataPart> parseMultiPartFormData(IHTTPSession session, byte[] buffer){
        List<MultiPartFormDataPart> partList = new ArrayList<MultiPartFormDataPart>();

        if (session.getMethod().compareTo(Method.POST) != 0 || !session.getHeaders().containsKey("content-type") ||
                !session.getHeaders().get("content-type").startsWith("multipart/form-data"))
            return partList;

        // Get boundary from content-type
        String contentType = session.getHeaders().get("content-type");
        String[] typeParams = contentType.split(";");
        String boundary = null;
        for(String typeParam: typeParams) {
            if(typeParam.trim().startsWith("boundary=")){
                boundary = typeParam.trim().substring(9);
                break;
            }
        }
        if(boundary == null)
            return partList;

        // Get parts without any boundarys
        List<byte[]> parts = parseMultiPartFormDataBinaryPart(boundary, buffer);

        // Get header lines and body
        byte[] crlf = "\r\n".getBytes();
        for(int partI=0; partI<parts.size(); partI++){
            MultiPartFormDataPart newPart = new MultiPartFormDataPart();
            byte[] partData = parts.get(partI);

            // Parse headers and body
            int lastCrlfStart = 0;
            int lastCrlfEnd = 0;
            for(int i=0; i<partData.length; i++) {
                boolean crlfFound = true;
                for(int crlfI=0; crlfI<crlf.length; crlfI++){
                    if(partData[i+crlfI] != crlf[crlfI]) {
                        crlfFound = false;
                        break;
                    }
                }
                if(crlfFound == false)
                    continue;
                int crlfStart = i;
                int crlfEnd = i+crlf.length;
                if (crlfStart - lastCrlfStart <=2) {
                    // End of header
                    newPart.data = Arrays.copyOfRange(partData, crlfEnd, partData.length);
                    break;
                } else {
                    newPart.header.add(new String(Arrays.copyOfRange(partData, lastCrlfEnd, crlfStart)));
                    i = crlfEnd-1;
                }
                lastCrlfStart = crlfStart;
                lastCrlfEnd = crlfEnd;
            }

            partList.add(newPart);
        }

        return partList;
    }

    /**
     * https://tools.ietf.org/html/rfc2046#section-5.1
     * @param boundaryStr
     * @param data
     * @return
     */
    protected static List<byte[]> parseMultiPartFormDataBinaryPart(String boundaryStr, byte[] data){
        List<byte[]> parts = new ArrayList<byte[]>();
        byte[] boundary = ("--"+boundaryStr).getBytes();
        byte[] boundaryEnd = ("--"+boundaryStr+"--").getBytes();
        byte[] crlf = "\r\n".getBytes();

        int boundaryCount = 0;
        int partStartOffset = -1;
        int partEndOffset = -1;

        // Naive byte comparison algorithm
        for(int i=0; i<data.length; i++) {

            // Find next boundary start
            boolean foundBoundary = true;
            for(int boundaryI=0; boundaryI<boundary.length; boundaryI++) {
                if(i+boundaryI>data.length-1) {
                    foundBoundary = false;
                    break;
                }
                if(data[i+boundaryI] != boundary[boundaryI]) {
                    foundBoundary = false;
                    break;
                }
            }
            if(foundBoundary == false)
                continue;

            // boundary looks like this:
            // <start1>[crlf]<start2>--boundary<end1>[--][whitespaces]crlf<end2>
            // leading crlf optional for first boundary
            // -- after boundary only for last boundary
            // whitespaces after boundary optional

            // Search for boundary start
            int boundaryStart2 = i;
            int boundaryStart1 = -1;

            if(i<crlf.length) // first boundary, no crlf before boundary
                boundaryStart1 = 0;
            else {
                boolean crlfFound = true;
                for (int crlfI = 0; crlfI<crlf.length; crlfI++){
                    if(data[i-crlf.length+crlfI] != crlf[crlfI]) {
                        crlfFound = false;
                        break;
                    }
                }
                if(crlfFound)
                    boundaryStart1 = i-crlf.length;
                else
                    boundaryStart1 = i; // last body part now has some garbage at the end
            }

            int boundaryEnd1 = i+boundary.length;
            int boundaryEnd2 = -1;
            // Find boundary end (boundary ends with crlf)
            for(int j=boundaryEnd1; j<data.length; j++) {
                boolean crlfFound = true;
                for (int crlfI = 0; crlfI<crlf.length; crlfI++){
                    if(j+crlfI>data.length-1) {
                        crlfFound = false;
                        break;
                    }
                    if(data[j+crlfI] != crlf[crlfI]) {
                        crlfFound = false;
                        break;
                    }
                }
                if(crlfFound) {
                    boundaryEnd2 = j+crlf.length;
                    break;
                }
            }
            if(boundaryEnd2 == -1) // crlf not found, broken data
                break;

            // First part
            if(boundaryCount == 0) {
                partStartOffset = boundaryEnd2;
                boundaryCount++;
                i = boundaryEnd2-1;
            } else {
                // Next Part
                partEndOffset = boundaryStart1;

                byte[] newPart = Arrays.copyOfRange(data, partStartOffset, partEndOffset);
                parts.add(newPart);

                partStartOffset = boundaryEnd2;
                boundaryCount++;
                i = boundaryEnd2-1;
            }
            // Check for last part
            boolean foundBoundaryEnd = true;
            for(int boundaryI=0; boundaryI<boundaryEnd.length; boundaryI++) {
                if(boundaryStart2+boundaryI>data.length-1) {
                    foundBoundaryEnd = false;
                    break;
                }
                if(data[boundaryStart2+boundaryI] != boundaryEnd[boundaryI]) {
                    foundBoundaryEnd = false;
                    break;
                }

            }
            if(foundBoundaryEnd) // that's it
                break;
        }

        return parts;
    }

    public static class MultiPartFormDataPart{

        public List<String> header = new ArrayList<String>();
        public byte[] data = null;
    }

    public static class Status implements Response.IStatus {

        private final int requestStatus;

        private final String description;

        public Status(int requestStatus, String description) {
            this.requestStatus = requestStatus;
            this.description = description;
        }

        @Override
        public String getDescription() {
            return "" + this.requestStatus + " " + this.description;
        }

        @Override
        public int getRequestStatus() {
            return this.requestStatus;
        }
    }
}

