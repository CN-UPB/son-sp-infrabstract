package sonata.kernel.placement.net;

import sonata.kernel.placement.config.PopResource;

public class LinkChain {

    // Datacenter names
    public PopResource srcDc;
    public PopResource dstDc;

    public String srcStack;
    public String dstStack;

    // Names defined by Translator
    public String srcServer;
    public String srcPort;
    public String dstServer;
    public String dstPort;

    // Names used by Emulator
    public String srcNode;
    public String srcInterface;
    public String dstNode;

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
}
