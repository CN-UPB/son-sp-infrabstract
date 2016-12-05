package sonata.kernel.placement.net;

import sonata.kernel.placement.config.PopResource;

import java.util.ArrayList;
import java.util.List;

public class LinkLoadbalance {

    public LinkPort srcPort;

    public List<LinkPort> dstPorts = new ArrayList<LinkPort>();

    public LinkLoadbalance(LinkPort srcPort, List<LinkPort> dstPorts){
        this.srcPort = srcPort;
        this.dstPorts.addAll(dstPorts);
    }

    public LinkLoadbalance(PopResource srcDc, String srcStack, String srcServer, String srcPort,
                           List<LinkPort> dstPorts){
        this.srcPort = new LinkPort(srcDc, srcStack, srcServer, srcPort);
        this.dstPorts.addAll(dstPorts);
    }
}
