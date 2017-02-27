package sonata.kernel.placement.net;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import sonata.kernel.placement.config.PopResource;

/**
 * Contains information about a datacenter node's port.
 */
public class LinkPort {

    /**
     * Datacenter the node belongs to
     */
    @JsonIgnore
    public PopResource pop;
    /**
     * Stack the node is a part of
     */
    public String stack;
    /**
     * Node's name
     */
    public String server;
    /**
     * Name of the node's port
     */
    public String port;

    /**
     * Creates a new port descriptor
     * @param pop node's datacenter
     * @param stack node's stack
     * @param server node's name
     * @param port node's port
     */
    public LinkPort(PopResource pop, String stack, String server, String port){
        this.pop = pop;
        this.stack = stack;
        this.server = server;
        this.port = port;
    }

    /**
     * Return the datacenter name
     * @return datacenter name
     */
    @JsonGetter("pop")
    public String getPopName(){
        return pop.getPopName();
    }

    /**
     * Compares this port to another port
     * @param obj another port
     * @return true if the given port describes the same port, else false
     */
    public boolean equals(Object obj){
        if(obj == null || !(obj instanceof LinkPort))
            return false;
        LinkPort port = (LinkPort) obj;
        if(pop.getPopName().equals(port.pop.getPopName()) &&
           stack.equals(port.stack) &&
           server.equals(port.server) &&
           this.port.equals(port.port))
            return true;
        return false;
    }

    /**
     * Returns an integer to uniquely identify this port
     * @return integer representing this code
     */
    public int hashCode(){
        return ((this.pop == null? "null" : this.pop.getPopName())+port+stack+server).hashCode();
    }
}
