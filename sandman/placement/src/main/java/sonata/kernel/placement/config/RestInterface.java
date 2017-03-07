package sonata.kernel.placement.config;

/**
 * Configuration details about the REST interface served.
 */
public class RestInterface {

    /**
     * Interface ip the REST server should listen to.
     * Use "0.0.0.0" to listen on all interface ips.
     */
    String serverIp;
    /**
     * Port the REST server should listen on.
     */
    int port;

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}

