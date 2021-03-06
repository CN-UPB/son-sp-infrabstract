package sonata.kernel.placement.service;

import org.apache.commons.collections.MultiHashMap;
import org.apache.commons.collections.MultiMap;
import org.apache.commons.lang3.tuple.Pair;
import sonata.kernel.VimAdaptor.commons.nsd.NetworkFunction;
import sonata.kernel.VimAdaptor.commons.nsd.ServiceDescriptor;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.vnfd.VnfDescriptor;

public class ServiceInstance {
	final static Logger logger = Logger.getLogger(ServiceInstance.class);

    public ServiceDescriptor service;

    /**
     * Maps connection point ids to virtual link names
     */
    public final Map<String, String> connectionPoints;

    // Maps unique vnf id from service descriptor to the vnf instance
    //public final Map<String, FunctionInstance> functions;

    public final Map<String, Map<String, FunctionInstance>> function_list;
    public final Map<String, AtomicInteger> vnf_uid;
    public final Map<String, AtomicInteger> vnf_vlinkid;

    public final Map<String, Stack<String>> vnf_uid_s;
    public final Map<String, Stack<String>> vnf_vlinkid_s;

    //public final Map<String, > vnf_uid_t;
    //public final Map<String, List<Boolean>> vnf_vlinkid_t;

    public final Map<String, Map<String, LinkInstance>> outerlink_list;
    public final Map<String, Map<String, LinkInstance>> innerlink_list;

    public List<Pair<Pair<String, String>, Pair<String, String>>> get_create_chain() {
        return create_chain;
    }

    public final List<Pair<Pair<String, String>, Pair<String, String>>> create_chain;


    public List<Pair<Pair<String, String>, Pair<String, String>>> get_delete_chain() {
        return delete_chain;
    }

    public final List<Pair<Pair<String, String>, Pair<String, String>>> delete_chain;

    public List<Pair<Pair<String, String>, List<String>>> getCustomized_chains() {return customized_chains;}

    public final List<Pair<Pair<String, String>, List<String>>> customized_chains;

    public List<Pair<String, String>> get_create_input_lb_links() {
        return create_input_lb_links;
    }

    public final List<Pair<String ,String>> create_input_lb_links;

    public List<Pair<String, String>> get_delete_input_lb_links() {
        return delete_input_lb_links;
    }

    public final List<Pair<String ,String>> delete_input_lb_links;

    protected Map<String, VnfDescriptor> nw_function_desc_map;
    protected Map<String, NetworkFunction> network_functions_db;






    //public final List<UnitInstance> units;

    // Maps virtual link id to LinkInstance
    // LinkInstances connect only units
    public final Map<String, LinkInstance> outerLinks;

    // Maps virtual link id to LinkInstance
    public final Map<String, LinkInstance> innerLinks;


    public ServiceInstance(){
    	logger.debug("Service Instance");
        //functions = new HashMap<String,FunctionInstance>();
        function_list = new HashMap<String, Map<String,FunctionInstance>>();
        vnf_uid = new HashMap<String, AtomicInteger>();
        vnf_vlinkid = new HashMap<String, AtomicInteger>();
        outerlink_list = new HashMap<String, Map<String, LinkInstance>>();
        innerlink_list = new HashMap<String, Map<String, LinkInstance>>();
        //units = new ArrayList<UnitInstance>();
        outerLinks = new HashMap<String, LinkInstance>();
        innerLinks = new HashMap<String, LinkInstance>();
        this.connectionPoints = new HashMap<String, String>();

        create_chain = new ArrayList<Pair<Pair<String, String>, Pair<String, String>>>();
        delete_chain = new ArrayList<Pair<Pair<String, String>, Pair<String, String>>>();

        customized_chains = new ArrayList<Pair<Pair<String, String>, List<String>>>();

        create_input_lb_links = new ArrayList<Pair<String, String>>();
        delete_input_lb_links = new ArrayList<Pair<String, String>>();

        vnf_uid_s = new HashMap<String, Stack<String>>();
        vnf_vlinkid_s = new HashMap<String, Stack<String>>();





    }

    public FunctionInstance getFunctionInstance(String VnfName)
    {
        for(Map.Entry<String, Map<String, FunctionInstance>> entry_m: function_list.entrySet())
        {
            if(entry_m.getValue().get("vnf_" + VnfName) != null){
                return entry_m.getValue().get("vnf_" + VnfName);
            }
        }
        return null;
    }

    public String findVnfIdFromVnfInstanceName(String VnfName)
    {
        for(Map.Entry<String, Map<String, FunctionInstance>> entry_m: function_list.entrySet())
        {
            if(entry_m.getValue().get("vnf_" + VnfName) != null)
                return entry_m.getKey();
        }
        return null;
    }

    public boolean updateDataCenterForVnfInstance(String VnfName, String DataCenter)
    {
        for(Map.Entry<String, Map<String, FunctionInstance>> entry_m: function_list.entrySet())
        {
            FunctionInstance f_inst = entry_m.getValue().get("vnf_" + VnfName);
            if(f_inst != null)
            {
                f_inst.data_center = DataCenter;
                return true;
            }
        }
        return false;
    }

    public String getDataCenterForVnf(String VnfName)
    {
        for(Map.Entry<String, Map<String, FunctionInstance>> entry_m: function_list.entrySet())
        {
            FunctionInstance f_inst = entry_m.getValue().get("vnf_" + VnfName);
            if(f_inst != null)
            {
                return f_inst.data_center;
            }
        }
        return null;
    }


    /*
    public LinkInstance findLinkInstanceByUnit(UnitInstance unit, String conPoint){

        LinkInstance link = null;
        for(LinkInstance linkInstance: outerLinks.values()) {
            for(Map.Entry<UnitInstance, String> entry: linkInstance.nodeList.entrySet()){
                UnitInstance entryUnit = entry.getKey();
                String portname = entry.getValue();
                if(unit.linkConnectsToUnit(linkInstance, conPoint))
                //if(entryUnit==unit && (entryUnit.links.get(portname)==linkInstance || entryUnit.links.get(entryUnit.aliasConnectionPoints.get(portname))==linkInstance))
                //if(entryUnit.connectionPoints.keySet().contains(portName) || entryUnit.aliasConnectionPoints.keySet().contains(portName))
                    return linkInstance;
            }
        }
        for(LinkInstance linkInstance: innerLinks.values()) {
            for(Map.Entry<UnitInstance, String> entry: linkInstance.nodeList.entrySet()) {
                UnitInstance entryUnit = entry.getKey();
                String portname = entry.getValue();
                if(unit.linkConnectsToUnit(linkInstance, conPoint))
                //if(entryUnit==unit && (entryUnit.links.get(portname)==linkInstance || entryUnit.links.get(entryUnit.aliasConnectionPoints.get(portname))==linkInstance))
                //if(entryUnit.connectionPoints.keySet().contains(portName) || entryUnit.aliasConnectionPoints.keySet().contains(portName))
                    return linkInstance;
            }
        }
        return link;
    }*/
}
