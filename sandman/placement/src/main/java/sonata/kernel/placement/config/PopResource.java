package sonata.kernel.placement.config;

import java.util.ArrayList;

/**
 * Details about datacenter configuration
 */
public class PopResource {

    /**
     * Name of the datacenter.
     * Must be identical to the datacenter name in the emulator.
     */
    String popName;
    /**
     * HTTP-URL to the Keystone endpoint of the datacenter
     * Example:
     * "http://127.0.0.1:5001/v2.0"
     */
    String endpoint;
    /**
     * HTTP-URL to the Chaining endpoint of the datacenter
     * Example:
     * "http://127.0.0.1:4000/"
     */
    String chainingEndpoint;
    /**
     * HTTP-URL to the Monitoring endpoint of the datacenter
     * "http://127.0.0.1:3000/"
     */
    String monitoringEndpoint;
    /**
     * Tenant id for the datacenter
     */
    String tenantName;
    /**
     * User id for the datacenter
     */
    String userName;
    /**
     * Password for the datacenter
     */
    String password;
    /**
     * List of network resources
     * Unused since network configuration is solely assigned by the emulator
     */
    ArrayList<NetworkResource> networks;
    /**
     * Details about the resources served by the datacenter
     */
    SystemResource resource;

    public SystemResource getResource() {
        return resource;
    }

    public void setResource(SystemResource resource) {
        this.resource = resource;
    }

    public String getPopName() {
        return popName;
    }

    public void setPopName(String popName) {
        this.popName = popName;
    }

    public ArrayList<NetworkResource> getNetworks() {
        return networks;
    }

    public void setNetworks(ArrayList<NetworkResource> networks) {
        this.networks = networks;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getChainingEndpoint() {
        return chainingEndpoint;
    }

    public void setChainingEndpoint(String chainingEndpoint) {
        this.chainingEndpoint = chainingEndpoint;
    }

    public String getMonitoringEndpoint() {
        return monitoringEndpoint;
    }

    public void setMonitoringEndpoint(String monitoringEndpoint) {
        this.monitoringEndpoint = monitoringEndpoint;
    }
}
