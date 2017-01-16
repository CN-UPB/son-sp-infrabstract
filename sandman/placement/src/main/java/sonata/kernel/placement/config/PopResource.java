package sonata.kernel.placement.config;

import java.util.ArrayList;

public class PopResource {

    String popName;
    String endpoint;
    String chainingEndpoint;
    String monitoringEndpoint;
    String tenantName;
    String userName;
    String password;

    ArrayList<NodeResource> nodes;
    ArrayList<NetworkResource> networks;
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

    public ArrayList<NodeResource> getNodes() {
        return nodes;
    }

    public void setNodes(ArrayList<NodeResource> nodes) {
        this.nodes = nodes;
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
