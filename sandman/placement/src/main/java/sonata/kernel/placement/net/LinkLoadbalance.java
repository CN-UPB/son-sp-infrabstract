package sonata.kernel.placement.net;

import sonata.kernel.placement.config.PopResource;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains information about a network loadbalance rule
 */
public class LinkLoadbalance {

    /**
     * Source port
     */
    public LinkPort srcPort;
    /**
     * List of destination ports that should be loadbalanced
     */
    public List<LinkPort> dstPorts = new ArrayList<LinkPort>();

    /**
     * Creates a new loadbalance rule
     * @param srcPort source port
     * @param dstPorts list of destination ports
     */
    public LinkLoadbalance(LinkPort srcPort, List<LinkPort> dstPorts){
        this.srcPort = srcPort;
        this.dstPorts.addAll(dstPorts);
    }

    /**
     * Creates a new loadbalance rule
     * @param srcDc source datacenter
     * @param srcStack source stack
     * @param srcServer source server
     * @param srcPort source server's port
     * @param dstPorts list of destination ports
     */
    public LinkLoadbalance(PopResource srcDc, String srcStack, String srcServer, String srcPort,
                           List<LinkPort> dstPorts){
        this.srcPort = new LinkPort(srcDc, srcStack, srcServer, srcPort);
        this.dstPorts.addAll(dstPorts);
    }
}
