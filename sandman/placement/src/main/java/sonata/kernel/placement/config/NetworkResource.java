package sonata.kernel.placement.config;

import java.util.ArrayList;

/**
 * Configuration for assignable network resources
 * Not in use since the emulator manages network configuration.
 */
public class NetworkResource {

    /**
     * Name of the set of network resources
     */
    String name;
    /**
     * Assignable subnet with netmask in CIDR notation
     * Defines the set of assignable ip addresses.
     */
    String subnet;
    /**
     * Gateway IP
     */
    String gateway;
    /**
     * Defines the network names that are preferably served from this network resources.
     * Example:
     * "mgmt"
     */
    String prefer;
    /**
     * Defines explicitly the set of assignable IP addresses.
     */
    ArrayList<String> available;

    public String getPrefer() {
        return prefer;
    }

    public void setPrefer(String prefer) {
        this.prefer = prefer;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubnet() {
        return subnet;
    }

    public void setSubnet(String subnet) {
        this.subnet = subnet;
    }

    public ArrayList<String> getAvailable() {
        return available;
    }

    public void setAvailable(ArrayList<String> available) {
        this.available = available;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }
}
