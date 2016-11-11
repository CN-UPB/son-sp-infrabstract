package sonata.kernel.placement.service;

import sonata.kernel.VimAdaptor.commons.nsd.VirtualLink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.vnfd.VnfVirtualLink;

public class LinkInstance {
	final static Logger logger = Logger.getLogger(LinkInstance.class);

    public final String name;

//    public String vnf_virtual;
//    public String vnf_real;

        // VirtualLink or VnfVirtualLink
    public final Object link;

    // FunctionInstance or UnitInstance maps to connection point that is used of that instance
    //public Map<UnitInstance, String> nodeList;

    public Map<FunctionInstance, String> interfaceList;


    public boolean isBuild_out() {
        return build_out;
    }

    public void setBuild_out(boolean build_out) {
        this.build_out = build_out;
    }

    public boolean build_out;

    public boolean isBuild_in() {
        return build_in;
    }

    public void setBuild_in(boolean build_in) {
        this.build_in = build_in;
    }

    public boolean build_in;

    public LinkInstance(Object link, String name){
    	logger.info("Link Instance");
        this.link = link;
        this.name = name;
        assert link instanceof VirtualLink || link instanceof VnfVirtualLink : "LinkInstance is based on a VirtualLink or a VnfVirtualLink";
        //nodeList = new HashMap<UnitInstance, String>();
        interfaceList = new HashMap<FunctionInstance, String>();
        setBuild_in(true);
        setBuild_out(true);

    }

    public String getLinkId(){
        if(link instanceof VirtualLink)
            return ((VirtualLink)link).getId();
        if(link instanceof VnfVirtualLink)
            return ((VnfVirtualLink)link).getId();
        return "";
    }

    public boolean isMgmtLink(){
        if(link instanceof VirtualLink) {
            VirtualLink vlink = (VirtualLink) link;
            if(vlink.getId().equals("mgmt"))
                return true;
            for(String ref: vlink.getConnectionPointsReference())
                if(ref.endsWith("mgmt"))
                    return true;
        }
        if(link instanceof VnfVirtualLink) {
            VnfVirtualLink vlink =  (VnfVirtualLink) link;
            if(vlink.getId().equals("mgmt"))
                return true;
            for(String ref: vlink.getConnectionPointsReference())
                if(ref.endsWith("mgmt"))
                    return true;
        }
        return false;
    }
}
