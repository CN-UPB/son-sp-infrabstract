package sonata.kernel.placement;

import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import sonata.kernel.placement.pd.PackageLoader;
import sonata.kernel.placement.pd.SonataPackage;
import sonata.kernel.placement.service.MonitorMessage;

/**
 * Serves REST API.
 * Implements a small subset of the Gatekeeper API to receive packages from the Editor.
 * Also implements a few API calls to control the Translator and request status information.
 */
class RestInterfaceServerApi extends NanoHTTPD implements Runnable {
    final Logger logger = Logger.getLogger(RestInterfaceServerApi.class);

    /**
     * Create a REST Server on port 8080
     */
    public RestInterfaceServerApi() {
        super(8080);
    }

    /**
     * Create a REST Server listening on given interface and port
     * @param hostname interface to listen to
     * @param port port to listen to
     * @throws IOException
     */
    public RestInterfaceServerApi(String hostname, int port) throws IOException {
        super(hostname, port);
        logger.info("Started RESTful server Hostname: "
                + hostname + " Port: " + port);
    }

    /**
     * Starts the REST server and blocks.
     * @throws IOException
     */
    public void start_server() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    /**
     * Server thread run method.
     * Starts the REST server
     */
    public void run() {
        try {
            start_server();
        } catch (IOException ioe) {
            logger.error("Failed to start server ",ioe);
        }
    }

    /**
     * Extracts a number from the given String
     * @param str String to extract number from
     * @return Extract number as String
     */
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

    /**
     * Create responses for HTTP requests to this REST server.
     * / - redirects to status frontend
     * /status - served by MiniStatusServer
     * /static - served by MiniStatusServer
     * /api/v2/packages - POST - receives a package to add to Catalogue
     * /api/v2/packages - GET - returns a list of packages in Catalogue as JSON array
     * /undeploy - undeploys the currently deployed service
     * /scaleout - triggers a fake scale out message
     * /scalein - triggers a fake scale in message
     * else - deploys a package with the given index
     *
     * @param session HTTP Request details
     * @return HTTP Response
     */
    @Override
    public Response serve(IHTTPSession session) {
        final Logger logger = Logger.getLogger(RestInterfaceServerApi.class);
        try {
            String uri = session.getUri();
            String req_uri = uri;

            if(uri.equals("/")){
                Response r = newFixedLengthResponse(new Status(302, "FOUND"),null, null);
                r.addHeader("Location","/static/status.html");
                return r;
            }
            else
            if(uri.startsWith("/status")) {
                return MiniStatusServer.serveDynamic(session);
            }
            else
            if(uri.startsWith("/static")) {
                return MiniStatusServer.serveStatic(session);
            }
            else
            if(("/api/v2/packages".equals(uri) || "/api/v2/packages?".equals(uri)) && session.getMethod().equals(Method.POST)) {
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
            if("/api/v2/packages".equals(uri) && session.getMethod().equals(Method.GET)) {
				logger.info("Package list");
                String jsonPackageList = Catalogue.getJsonPackageList();
                String ret_index=null;
                JSONObject jsonObj = new JSONObject();
            	JSONArray jsonArray = new JSONArray();
                for (int i = 0;i < Catalogue.packages.size();i++){
                	String str = String.valueOf(i);
                	jsonArray.put(str);
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
            if("/scaleout".equals(uri) && session.getMethod().equals(Method.GET)) {
                logger.info("Fake Scale Out Message");

                MessageQueue.MessageQueueMonitorData message = new MessageQueue.MessageQueueMonitorData(null);
                message.fakeScaleType = MonitorMessage.SCALE_TYPE.SCALE_OUT;
                MessageQueue.get_deploymentQ().put(message);

                // do not wait for any response
                return newFixedLengthResponse(new Status(200, "OK"), null, null);
            }
            else
            if("/scalein".equals(uri) && session.getMethod().equals(Method.GET)) {
                logger.info("Fake Scale In Message");

                MessageQueue.MessageQueueMonitorData message = new MessageQueue.MessageQueueMonitorData(null);
                message.fakeScaleType = MonitorMessage.SCALE_TYPE.SCALE_IN;
                MessageQueue.get_deploymentQ().put(message);

                // do not wait for any response
                return newFixedLengthResponse(new Status(200, "OK"), null, null);
            }
            else
            if(req_uri.equals(uri) && session.getMethod().equals(Method.POST)) {
                logger.info("Deploy");
            	int newIndex;
            	Integer contentLength = Integer.parseInt(session.getHeaders().get("content-length"));
            	byte[] buffer = new byte[contentLength];
            	session.getInputStream().read(buffer, 0, contentLength);
            	String str = new String(buffer);
            	String index = extractNumber(str);
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

    /**
     * Lazy method to get the multipart/form-data body.
     * Assumes that the body contains only one part.
     * Strips the first border and the header lines.
     * Returns the rest.
     * @param session HTTP Request details
     * @param buffer HTTP Request Content Body
     * @return multipart/form-data body with stripped first border and header lines
     */
    public static byte[] stripMultiPartFormDataHeader(IHTTPSession session, byte[] buffer) {
        // Check if POST request contains multipart/form-data
        if (session.getMethod().compareTo(Method.POST) == 0 && session.getHeaders().containsKey("content-type") &&
                (session.getHeaders().get("content-type").startsWith("multipart/form-data"))) {

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

    /**
     * Extracts parts from a multipart/form-data body.
     * Uses boundary to extract header lines and body from the parts of a multipart/form-data body.
     * @param session HTTP Request details
     * @param buffer Request body
     * @return List of multipart/form-data parts
     */
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
     * Splits multipart/form-data body into parts without boundaries.
     * https://tools.ietf.org/html/rfc2046#section-5.1
     * @param boundaryStr Boundary to search for
     * @param data Request body
     * @return List of multipart/form-data parts as byte[] containing header lines and binary body without boundaries
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

    /**
     * Part of a multipart/form-data request body
     */
    public static class MultiPartFormDataPart{

        /**
         * Header lines defined in this part
         */
        public List<String> header = new ArrayList<String>();
        /**
         * Body data defined in this part
         */
        public byte[] data = null;
    }

    /**
     * Allows for arbitrary HTTP Response Status
     */
    public static class Status implements Response.IStatus {

        /**
         * HTTP Response Status Code
         */
        private final int requestStatus;

        /**
         * HTTP Response Status Message
         */
        private final String description;

        /**
         * Create a new HTTP Response Status
         * @param requestStatus Status Code
         * @param description Status Message
         */
        public Status(int requestStatus, String description) {
            this.requestStatus = requestStatus;
            this.description = description;
        }

        /**
         * Returns informational String about this status
         * @return
         */
        @Override
        public String getDescription() {
            return "" + this.requestStatus + " " + this.description;
        }

        /**
         * Returns Status Code
         * @return
         */
        @Override
        public int getRequestStatus() {
            return this.requestStatus;
        }
    }
}

