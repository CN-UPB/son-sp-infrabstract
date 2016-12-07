package sonata.kernel.placement.net;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import sonata.kernel.placement.config.PopResource;

public class LinkPort {

    @JsonIgnore
    public PopResource pop;
    public String stack;
    public String server;
    public String port;

    public LinkPort(PopResource pop, String stack, String server, String port){
        this.pop = pop;
        this.stack = stack;
        this.server = server;
        this.port = port;
    }

    @JsonGetter("pop")
    public String getPopName(){
        return pop.getPopName();
    }

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

    public int hashCode(){
        return ((this.pop == null? "null" : this.pop.getPopName())+port+stack+server).hashCode();
    }
}
