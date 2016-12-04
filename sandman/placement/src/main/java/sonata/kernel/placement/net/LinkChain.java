package sonata.kernel.placement.net;

import sonata.kernel.placement.config.PopResource;

public class LinkChain {

    public LinkPort srcPort;
    public LinkPort dstPort;

    public LinkChain(PopResource srcDc, String srcStack, String srcServer, String srcPort,
                     PopResource dstDc, String dstStack, String dstServer, String dstPort) {
        this.srcPort = new LinkPort(srcDc, srcStack, srcServer, srcPort);
        this.dstPort = new LinkPort(dstDc, dstStack, dstServer, dstPort);
    }

    public LinkChain(LinkPort srcPort, LinkPort dstPort){
        this.srcPort = srcPort;
        this.dstPort = dstPort;
    }

    public boolean equals(LinkChain chain){
        if(chain == null)
            return false;
        if(srcPort.equals(chain.srcPort) && dstPort.equals(chain.dstPort))
            return true;
        return false;
    }
}
