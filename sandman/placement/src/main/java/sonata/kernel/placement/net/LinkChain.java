package sonata.kernel.placement.net;

import sonata.kernel.placement.config.PopResource;
import java.util.List;

/**
 * Contains information about the network chain between two datacenter nodes.
 */
public class LinkChain {

    /**
     * The chain's source port
     */
    public LinkPort srcPort;
    /**
     * The chain's destination port
     */
    public LinkPort dstPort;
    /**
     * Optional list of switches used for the chain
     */
    public List<String> path = null;

    /**
     * Creates a new chain descriptor
     * @param srcDc source datacenter
     * @param srcStack source stack
     * @param srcServer source server name
     * @param srcPort source server's port
     * @param dstDc destination datacenter
     * @param dstStack destination stack
     * @param dstServer destination server
     * @param dstPort destination server's port
     */
    public LinkChain(PopResource srcDc, String srcStack, String srcServer, String srcPort,
                     PopResource dstDc, String dstStack, String dstServer, String dstPort) {
        this.srcPort = new LinkPort(srcDc, srcStack, srcServer, srcPort);
        this.dstPort = new LinkPort(dstDc, dstStack, dstServer, dstPort);
    }

    /**
     * Creates a new chain descriptor
     * @param srcPort source port
     * @param dstPort destination port
     */
    public LinkChain(LinkPort srcPort, LinkPort dstPort){
        this.srcPort = srcPort;
        this.dstPort = dstPort;
    }

    /**
     * Compares this chain to another chain descriptor
     * @param obj another chain descriptor
     * @return true if the given chain describes the same chain, else false
     */
    public boolean equals(Object obj){
        if(obj == null || !(obj instanceof LinkChain))
            return false;
        LinkChain chain = (LinkChain) obj;
        if(srcPort.equals(chain.srcPort) && dstPort.equals(chain.dstPort))
            return true;
        return false;
    }
}
