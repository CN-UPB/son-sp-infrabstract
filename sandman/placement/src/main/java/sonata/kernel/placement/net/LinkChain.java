package sonata.kernel.placement.net;

import sonata.kernel.placement.config.PopResource;

public class LinkChain {

    // Datacenter names
    public PopResource srcDc;
    public PopResource dstDc;

    public String srcStack;
    public String dstStack;

    public String srcServer;
    public String srcPort;
    public String dstServer;
    public String dstPort;

    public LinkChain(PopResource srcDc, String srcStack, String srcServer, String srcPort,
                     PopResource dstDc, String dstStack, String dstServer, String dstPort) {
        this.srcDc = srcDc;
        this.dstDc = dstDc;
        this.srcStack = srcStack;
        this.dstStack = dstStack;
        this.srcServer = srcServer;
        this.srcPort = srcPort;
        this.dstServer = dstServer;
        this.dstPort = dstPort;

    }

    public boolean equals(LinkChain chain){
        if(chain == null)
            return false;
        if(srcDc.getPopName().equals(chain.srcDc.getPopName()) &&
           dstDc.getPopName().equals(chain.dstDc.getPopName()) &&
           srcStack.equals(chain.srcStack) &&
           dstStack.equals(chain.dstStack) &&
           srcServer.equals(chain.srcServer) &&
           dstServer.equals(chain.dstServer) &&
           srcPort.equals(chain.srcPort) &&
           dstPort.equals(chain.dstPort))
            return true;
        return false;
    }
}
