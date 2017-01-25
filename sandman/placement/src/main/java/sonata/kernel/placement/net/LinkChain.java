package sonata.kernel.placement.net;

import sonata.kernel.placement.config.PopResource;

import java.util.ArrayList;
import java.util.List;

public class LinkChain {

    public LinkPort srcPort;
    public LinkPort dstPort;
    public List<String> path = new ArrayList<String>();

    public LinkChain(PopResource srcDc, String srcStack, String srcServer, String srcPort,
                     PopResource dstDc, String dstStack, String dstServer, String dstPort) {
        this.srcPort = new LinkPort(srcDc, srcStack, srcServer, srcPort);
        this.dstPort = new LinkPort(dstDc, dstStack, dstServer, dstPort);
    }

    public LinkChain(LinkPort srcPort, LinkPort dstPort){
        this.srcPort = srcPort;
        this.dstPort = dstPort;
    }

    public boolean equals(Object obj){
        if(obj == null || !(obj instanceof LinkChain))
            return false;
        LinkChain chain = (LinkChain) obj;
        if(srcPort.equals(chain.srcPort) && dstPort.equals(chain.dstPort))
            return true;
        return false;
    }
}
