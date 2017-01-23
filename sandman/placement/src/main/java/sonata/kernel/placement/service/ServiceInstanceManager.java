package sonata.kernel.placement.service;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import sonata.kernel.VimAdaptor.commons.DeployServiceData;
import sonata.kernel.VimAdaptor.commons.nsd.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.vnfd.VnfDescriptor;
import sonata.kernel.VimAdaptor.commons.vnfd.VnfVirtualLink;
import sonata.kernel.placement.Catalogue;
import sonata.kernel.placement.PlacementConfigLoader;
import sonata.kernel.placement.config.PlacementConfig;

/*
ServiceInstanceManager enables addition/deletion/updation of resources
for a given the ServiceInstance pertaining to the SONATA descriptor.
 */

public class ServiceInstanceManager {

    final static Logger logger = Logger.getLogger(ServiceInstanceManager.class);

    public enum ACTION_TYPE {
        ADD_INSTANCE,
        DELETE_INSTANCE
    }

    public ServiceInstance get_instance() {
        return instance;
    }

    public void set_instance(ServiceInstance instance) {
        this.instance = instance;
    }

    private ServiceInstance instance;
    private Map<String, VnfDescriptor> nw_function_desc_map;
    private Map<String, NetworkFunction> network_functions_db;
    private String default_pop;
    private PlacementConfig config;

    public ServiceInstance initialize_service_instance(DeployServiceData service_data) {
        ServiceDescriptor service = service_data.getNsd();

        instance = new ServiceInstance();
        instance.service = service;

        config = PlacementConfigLoader.loadPlacementConfig();
        default_pop = config.getResources().get(0).getPopName();

        String uuid = service.getUuid();
        String instance_uuid = service.getInstanceUuid();
        String service_name = service.getName();
        ArrayList<ConnectionPoint> connection_points = Lists.newArrayList(service.getConnectionPoints());
        ArrayList<ForwardingGraph> forwarding_graph = Lists.newArrayList(service.getForwardingGraphs());

        initialize_function_instance(service_data);
        initialize_vlinks_list(service_data);


        return instance;


    }

    protected void initialize_function_instance(DeployServiceData service_data) {
        nw_function_desc_map = new HashMap<String, VnfDescriptor>();
        network_functions_db = new HashMap<String, NetworkFunction>();
        ArrayList<NetworkFunction> network_functions = Lists.newArrayList(instance.service.getNetworkFunctions());

        for (VnfDescriptor descriptor : service_data.getVnfdList()) {
            nw_function_desc_map.put(descriptor.getName(), descriptor);
            logger.debug("VNF Descriptor " + descriptor);
        }

        for (NetworkFunction function : network_functions) {


            network_functions_db.put(function.getVnfId(), function);
            VnfDescriptor descriptor = nw_function_desc_map.get(function.getVnfName());
            assert descriptor != null : "Virtual Network Function " + function.getVnfName() + " not found";

            FunctionInstance function_instance = new FunctionInstance(function, descriptor, function.getVnfId(), default_pop);

            int id;

            if (null == instance.function_list.get(function.getVnfId())) {
                AtomicInteger vnf_uid = new AtomicInteger(0);
                id = vnf_uid.addAndGet(1);
                vnf_uid.set(id);
                instance.vnf_uid.put(function.getVnfId(), vnf_uid);
                Map<String, FunctionInstance> map = new HashMap<String, FunctionInstance>();
                map.put(function.getVnfId() + id, function_instance);
                instance.function_list.put(function.getVnfId(), map);


            } else {
                id = instance.vnf_uid.get(function.getVnfId()).addAndGet(1);
                instance.vnf_uid.get(function.getVnfId()).set(id);
                instance.function_list.get(function.getVnfId()).put(function.getVnfId() +
                        id, function_instance);
            }


            function_instance.setName(function.getVnfId().split("_")[1] + id);

            initialize_vnfvlink_list(function_instance, descriptor);
        }
    }

    protected void initialize_vnfvlink_list(FunctionInstance f_instance, VnfDescriptor descriptor) {
        for (VnfVirtualLink link : descriptor.getVirtualLinks()) {

            LinkInstance linkInstance = new LinkInstance(link, "vnflink:" + f_instance.name + ":" + link.getId());
            boolean is_outerlink = false;

            for (String ref : link.getConnectionPointsReference()) {
                String[] conPointParts = ref.split(":");
                if ("vnf".equals(conPointParts[0])) {
                    is_outerlink = true;
                    continue;
                }
                //linkInstance.interfaceList.put(f_instance, ref);
                linkInstance.interfaceList.put(f_instance, ref);
            }
            if (is_outerlink) {
                f_instance.links.put(link.getId(), linkInstance);
            }

        }
    }

    protected void add_link(VirtualLink link, LinkInstance linkInstance, String src, String target, boolean build_in, boolean build_out) {
        boolean is_nslink = false;

        for (String cp_ref : link.getConnectionPointsReference()) {

            String[] cp_ref_str = cp_ref.split(":");
            assert cp_ref_str != null && cp_ref_str.length == 2 : "Virtual Link " + link.getId() + " uses odd vnf reference " + cp_ref;
            String vnfid = cp_ref_str[0];
            String connectionPointName = cp_ref_str[1];

            if ("ns".equals(vnfid)) {
                is_nslink = true;
                continue;
            }
            if (src.contains(cp_ref.split(":")[0]))
                linkInstance.interfaceList.put(instance.function_list.get(cp_ref.split(":")[0]).get(src), cp_ref);
            else
                linkInstance.interfaceList.put(instance.function_list.get(cp_ref.split(":")[0]).get(target), cp_ref);

        }

        linkInstance.setBuild_in(build_in);
        linkInstance.setBuild_out(build_out);

        int id;
        if (is_nslink) {
            if (instance.outerlink_list.get(link.getId()) == null) {
                AtomicInteger vnf_vlinkid = new AtomicInteger(0);
                id = vnf_vlinkid.addAndGet(1);
                vnf_vlinkid.set(id);
                instance.vnf_vlinkid.put(link.getId(), vnf_vlinkid);
                Map<String, LinkInstance> map = new HashMap<String, LinkInstance>();
                map.put(link.getId() + ":" + id, linkInstance);
                instance.outerlink_list.put(link.getId(), map);
            } else {
                id = instance.vnf_vlinkid.get(link.getId()).addAndGet(1);
                instance.vnf_vlinkid.get(link.getId()).set(id);
                instance.outerlink_list.get(link.getId()).put(link.getId() + ":" + id, linkInstance);
            }
            instance.outerLinks.put(link.getId(), linkInstance);

        } else {
            if (instance.innerlink_list.get(link.getId()) == null) {
                AtomicInteger vnf_vlinkid = new AtomicInteger(0);
                id = vnf_vlinkid.addAndGet(1);
                vnf_vlinkid.set(id);
                instance.vnf_vlinkid.put(link.getId(), vnf_vlinkid);
                Map<String, LinkInstance> map = new HashMap<String, LinkInstance>();
                map.put(link.getId() + ":" + id, linkInstance);
                instance.innerlink_list.put(link.getId(), map);
            } else {
                id = instance.vnf_vlinkid.get(link.getId()).addAndGet(1);
                instance.vnf_vlinkid.get(link.getId()).set(id);
                instance.innerlink_list.get(link.getId()).put(link.getId() + ":" + id, linkInstance);
            }

        }
        return;
    }

    protected void internal_add_mgmt_link(FunctionInstance f_inst){

        logger.debug("Add internal mgmt link "+f_inst.getName());
        VirtualLink link = new VirtualLink();
        link.setId("mgmt");
        LinkInstance linkInstance = new LinkInstance(link, "nslink:mgmt");
        linkInstance.interfaceList.put(f_inst, f_inst.function.getVnfId()+":mgmt");

        int id;
        if (instance.outerlink_list.get(link.getId()) == null) {
            AtomicInteger vnf_vlinkid = new AtomicInteger(0);
            id = vnf_vlinkid.addAndGet(1);
            vnf_vlinkid.set(id);
            instance.vnf_vlinkid.put(link.getId(), vnf_vlinkid);
            Map<String, LinkInstance> map = new HashMap<String, LinkInstance>();
            map.put(link.getId() + ":" + id, linkInstance);
            instance.outerlink_list.put(link.getId(), map);
        } else {
            id = instance.vnf_vlinkid.get(link.getId()).addAndGet(1);
            instance.vnf_vlinkid.get(link.getId()).set(id);
            instance.outerlink_list.get(link.getId()).put(link.getId() + ":" + id, linkInstance);
        }
    }

    protected void update_ns_link(FunctionInstance f_inst)
    {
        ArrayList<VirtualLink> virtual_links = Lists.newArrayList(instance.service.getVirtualLinks());

        for (VirtualLink link : virtual_links) {
            LinkInstance linkInstance = new LinkInstance(link, "nslink:" + link.getId());

            boolean do_not = true;
            boolean is_nslink = false;

            for (String cp_ref : link.getConnectionPointsReference()) {
System.out.println("update_ns_link "+link.getId()+"  "+cp_ref);
                String[] cp_ref_str = cp_ref.split(":");
                assert cp_ref_str != null && cp_ref_str.length == 2 : "Virtual Link " + link.getId() + " uses odd vnf reference " + cp_ref;
                String vnfid = cp_ref_str[0];
                String connectionPointName = cp_ref_str[1];

                if ("ns".equals(vnfid)) {
                    is_nslink = true;
                    continue;
                }

                if (f_inst.function.getVnfId().equals(vnfid) == true) {
                    LinkInstance vnf_LinkInstance = f_inst.links.get(connectionPointName);
                    assert vnf_LinkInstance != null : "In Service " + instance.service.getName() + " Virtual Link "
                            + link.getId() + " connects to function " + f_inst.name
                            + " that does not contain link for connection point " + connectionPointName;
                    linkInstance.interfaceList.put(f_inst, cp_ref);
                    do_not = false; //Do not add to outerlink list. Not a NS link.
                }

            }
            if(do_not)
                continue;

            //if(linkInstance.isMgmtLink())
            //    continue;

            int id;
            if (is_nslink) {
                System.out.println("Add to outerlink list");
                if (instance.outerlink_list.get(link.getId()) == null) {
                    AtomicInteger vnf_vlinkid = new AtomicInteger(0);
                    id = vnf_vlinkid.addAndGet(1);
                    vnf_vlinkid.set(id);
                    instance.vnf_vlinkid.put(link.getId(), vnf_vlinkid);
                    Map<String, LinkInstance> map = new HashMap<String, LinkInstance>();
                    map.put(link.getId() + ":" + id, linkInstance);
                    instance.outerlink_list.put(link.getId(), map);
                } else {
                    id = instance.vnf_vlinkid.get(link.getId()).addAndGet(1);
                    instance.vnf_vlinkid.get(link.getId()).set(id);
                    instance.outerlink_list.get(link.getId()).put(link.getId() + ":" + id, linkInstance);
                }
            }
        }

    }

    protected void delete_ns_link(String f_inst)
    {
        String key="";
        for(Map.Entry<String, Map<String, LinkInstance>> link_ll : instance.outerlink_list.entrySet())
        {
            for(Map.Entry<String, LinkInstance> link_e : link_ll.getValue().entrySet())
            {
                for(Map.Entry<FunctionInstance, String> finst : link_e.getValue().interfaceList.entrySet()){
                    if(finst.getKey().name.equals(f_inst)) {
                        key = link_e.getKey();
                    }
                }
            }
        }

        instance.outerlink_list.get((key.split(":"))[0]).remove(key);
        return;
}

    protected void initialize_link(VirtualLink link, LinkInstance linkInstance) {
        boolean is_nslink = false;

        for (String cp_ref : link.getConnectionPointsReference()) {

            String[] cp_ref_str = cp_ref.split(":");
            assert cp_ref_str != null && cp_ref_str.length == 2 : "Virtual Link " + link.getId() + " uses odd vnf reference " + cp_ref;
            String vnfid = cp_ref_str[0];
            String connectionPointName = cp_ref_str[1];

            if ("ns".equals(vnfid)) {
                is_nslink = true;
                continue;
            }

            Map<String, FunctionInstance> vnf_instances = instance.function_list.get(vnfid);
            assert vnf_instances.size() != 0 : "In Service " + instance.service.getName() + " Virtual Link " + link.getId() + " references unknown vnf with id " + vnfid;

            for (Map.Entry<String, FunctionInstance> finst : vnf_instances.entrySet()) {
                LinkInstance vnfLinkInstance = finst.getValue().links.get(connectionPointName);
                assert vnfLinkInstance != null : "In Service " + instance.service.getName() + " Virtual Link "
                        + link.getId() + " connects to function " + finst.getValue().name
                        + " that does not contain link for connection point " + connectionPointName;

                linkInstance.interfaceList.put(finst.getValue(), cp_ref);



                /*
                port.setName(finst.getValue().getName() + ":" + conPointParts[1]
                        + ":" + link1.getLinkId() + ":" + instance.service.getInstanceUuid());
                 */
            }

        }

        int id;
        if (is_nslink) {
            if (instance.outerlink_list.get(link.getId()) == null) {
                AtomicInteger vnf_vlinkid = new AtomicInteger(0);
                id = vnf_vlinkid.addAndGet(1);
                vnf_vlinkid.set(id);
                instance.vnf_vlinkid.put(link.getId(), vnf_vlinkid);
                Map<String, LinkInstance> map = new HashMap<String, LinkInstance>();
                map.put(link.getId() + ":" + id, linkInstance);
                instance.outerlink_list.put(link.getId(), map);
            } else {
                id = instance.vnf_vlinkid.get(link.getId()).addAndGet(1);
                instance.vnf_vlinkid.get(link.getId()).set(id);
                instance.outerlink_list.get(link.getId()).put(link.getId() + ":" + id, linkInstance);
            }
            instance.outerLinks.put(link.getId(), linkInstance);

        } else {
            if (instance.innerlink_list.get(link.getId()) == null) {
                AtomicInteger vnf_vlinkid = new AtomicInteger(0);
                id = vnf_vlinkid.addAndGet(1);
                vnf_vlinkid.set(id);
                instance.vnf_vlinkid.put(link.getId(), vnf_vlinkid);
                Map<String, LinkInstance> map = new HashMap<String, LinkInstance>();
                map.put(link.getId() + ":" + id, linkInstance);
                instance.innerlink_list.put(link.getId(), map);
            } else {
                id = instance.vnf_vlinkid.get(link.getId()).addAndGet(1);
                instance.vnf_vlinkid.get(link.getId()).set(id);
                instance.innerlink_list.get(link.getId()).put(link.getId() + ":" + id, linkInstance);
            }

        }

        add_chaining_rules(linkInstance, instance.create_chain, null);


        return;
    }

    public void flush_chaining_rules()
    {
        instance.create_chain.clear();
        instance.delete_chain.clear();
        instance.customized_chains.clear();
    }

    protected void delete_chaining_rules(String link_id, String link_name) {

        LinkInstance linkInstance = instance.innerlink_list.get(link_id).get(link_name);
        add_chaining_rules(linkInstance, instance.delete_chain, null);
        return;
    }

    protected void add_chaining_rules(LinkInstance linkInstance,
                                      List<Pair<Pair<String, String>, Pair<String, String>>> chain, List<String> viaPath) {
        Object[] finst_t = linkInstance.interfaceList.entrySet().toArray();

        if (linkInstance.isMgmtLink())
            return;

        //nslink:input and output cases
        if (finst_t.length < 2)
            return;

        //E-LINE links
        String server[] = new String[2];
        String port[] = new String[2];
        String intf[] = new String[2];

        for (int i = 0; i < 2; i++) {
            for (Object v_link : (((HashMap.Entry<FunctionInstance, String>) finst_t[i]).getKey().descriptor.getVirtualLinks()).toArray()) {
                if (((VnfVirtualLink) v_link).getId().equals(((HashMap.Entry<FunctionInstance, String>) finst_t[i]).getValue().split(":")[1])) {
                    for (String if_name : ((VnfVirtualLink) v_link).getConnectionPointsReference()) {
                        if (if_name.contains("vdu")) {
                            intf[i] = if_name.split(":")[1];
                        }
                    }
                }
            }
            port[i] = ((HashMap.Entry<FunctionInstance, String>) finst_t[i]).getKey().getName() + ":" + intf[i]
                    + ":" + ((HashMap.Entry<FunctionInstance, String>) finst_t[i]).getValue().split(":")[1]
                    + ":" + instance.service.getInstanceUuid();
            server[i] = ((HashMap.Entry<FunctionInstance, String>) finst_t[i]).getKey().getName() + ":" +
                    instance.service.getInstanceUuid();
        }


        if (((HashMap.Entry<FunctionInstance, String>) finst_t[1]).getValue().split(":")[1].equals("input")) {
            chain.add(new ImmutablePair<Pair<String,String>,Pair<String,String>>(new ImmutablePair<String,String>(server[0],port[0]), new ImmutablePair<String,String>(server[1],port[1])));
        } else {
            chain.add(new ImmutablePair<Pair<String,String>,Pair<String,String>>(new ImmutablePair<String,String>(server[1],port[1]), new ImmutablePair<String,String>(server[0],port[0])));
        }

        if(viaPath != null && viaPath.size()!=0)
        {
            if (((HashMap.Entry<FunctionInstance, String>) finst_t[1]).getValue().split(":")[1].equals("input")) {
                instance.customized_chains.add(new ImmutablePair<Pair<String, String>, List<String>>
                        (new ImmutablePair<String, String>(server[0], server[1]), viaPath));
            } else {
                instance.customized_chains.add(new ImmutablePair<Pair<String, String>, List<String>>
                        (new ImmutablePair<String, String>(server[1], server[0]), viaPath));
            }

        }
        return;

    }

    protected void initialize_vlinks_list(DeployServiceData service_data) {
        ArrayList<VirtualLink> virtual_links = Lists.newArrayList(instance.service.getVirtualLinks());

        for (VirtualLink link : virtual_links) {
            LinkInstance linkInstance = new LinkInstance(link, "nslink:" + link.getId());

            boolean is_nslink = false;

            initialize_link(link, linkInstance);
        }

    }

    public void update_vnfdescriptor(String vnf_id, boolean new_addition) {
        if (instance.function_list.get(vnf_id) == null && !new_addition) {
            logger.info("ServiceInstanceManager:update_vnfdescriptor ingored for " + vnf_id + ".");
            return;
        } else {
            VnfDescriptor descriptor = Catalogue.internalFunctions.get(vnf_id);
            nw_function_desc_map.put(network_functions_db.get(vnf_id).getVnfName(), descriptor);

            logger.info("ServiceInstanceManager:update_vnfdescriptor updated for " + vnf_id + ". "
                    + "VnfDescriptor: " + descriptor.getName() + " Version: " + descriptor.getVendor());
            return;
        }
    }

    /*
    Note:
    In case of vnf instance addition, vnf_name = null
    In case of vnf instance deletion, vnf_name must be the name associated with the vnf instance.
     */
    public String update_functions_list(String vnf_id, String vnf_name, String PopName, ACTION_TYPE action) {

        if (action == ACTION_TYPE.ADD_INSTANCE) {

            if (instance.function_list.get(vnf_id) == null) {
            /*
            Special network functions: Load balancers etc.
             */
                logger.debug("Add instance of unknown function "+vnf_id);
                Catalogue.loadInternalFunctions();
                VnfDescriptor descriptor = Catalogue.internalFunctions.get(vnf_id);
                assert descriptor != null : "Virtual Network Function " + vnf_id + " not found";


                NetworkFunction n_function = new NetworkFunction();
                n_function.setVnfId(vnf_id);
                n_function.setVnfName(descriptor.getName());
                n_function.setVnfVersion(descriptor.getVersion());
                n_function.setVnfVendor(descriptor.getVendor());
                n_function.setDescription(descriptor.getDescription());


                network_functions_db.put(vnf_id, n_function);

                String ss = network_functions_db.get(vnf_id).getVnfName();
                //nw_function_desc_map.put(descriptor.getName(), descriptor);
                nw_function_desc_map.put(ss, descriptor);

                AtomicInteger vnf_uid = new AtomicInteger(0);
                int id = vnf_uid.addAndGet(1);
                vnf_uid.set(id);
                instance.vnf_uid.put(n_function.getVnfId(), vnf_uid);

                FunctionInstance function_instance = new FunctionInstance(n_function, descriptor,
                        n_function.getVnfName().split("-")[0] + id, PopName);

                initialize_vnfvlink_list(function_instance, descriptor);

                Map<String, FunctionInstance> map = new HashMap<String, FunctionInstance>();
                map.put(n_function.getVnfId() + id, function_instance);
                instance.function_list.put(n_function.getVnfId(), map);

                internal_add_mgmt_link(function_instance);

                return n_function.getVnfId() + id;

            } else {
                logger.debug("Add instance of known function "+vnf_id);
                VnfDescriptor descriptor = nw_function_desc_map.get(network_functions_db.get(vnf_id).getVnfName());
                assert descriptor != null : "Virtual Network Function " + vnf_id + " not found";

                NetworkFunction n_function = network_functions_db.get(vnf_id);


                int id = instance.vnf_uid.get(n_function.getVnfId()).addAndGet(1);
                instance.vnf_uid.get(n_function.getVnfId()).set(id);
                FunctionInstance function_instance = new FunctionInstance(n_function, descriptor,
                        n_function.getVnfName().split("-")[0] + id, PopName);

                initialize_vnfvlink_list(function_instance, descriptor);

                update_ns_link(function_instance);

                instance.function_list.get(n_function.getVnfId()).put(n_function.getVnfId() +
                        id, function_instance);

                return n_function.getVnfId() + id;

            }
        } else if (action == ACTION_TYPE.DELETE_INSTANCE) {
            logger.debug("Delete instance of function "+vnf_id+" with name "+vnf_name);
            if (instance.function_list.get(vnf_id) == null) {
                logger.error("Virtual Network Function " + vnf_id + " not found");
            } else {
                delete_ns_link((vnf_name.split("_"))[1]);
                int id = instance.vnf_uid.get(vnf_id).decrementAndGet();
                instance.vnf_uid.get(vnf_id).set(id);
                instance.function_list.get(vnf_id).remove(vnf_name);
            }
        }
        return null;

    }

    public ServiceInstance update_vlink_list(String s_vnfid, String d_vnfid, String endpoint_src, String endpoint_target, List<String> viaPath, ACTION_TYPE action) {

        assert instance.function_list.get(s_vnfid) != null : "Virtual Network Function " + s_vnfid + " not found";
        assert instance.function_list.get(s_vnfid).get(endpoint_src) != null : "Virtual Network Function instance "
                + endpoint_src + " not found";
        assert instance.function_list.get(d_vnfid) != null : "Virtual Network Function" + d_vnfid + " not found";
        assert instance.function_list.get(d_vnfid).get(endpoint_target) != null : "Virtual Network Function instance "
                + endpoint_target + " not found";

        if (action == ACTION_TYPE.ADD_INSTANCE) {

            String link_name = null;
            String link_id = null;

            VirtualLink link = new VirtualLink();
            link.setId(s_vnfid.split("_")[1] + "-2-" + d_vnfid.split("_")[1]);
            link.setConnectivityType(VirtualLink.ConnectivityType.E_LINE);
            ArrayList<String> cp = new ArrayList<String>();
            cp.add(s_vnfid + ":output");
            cp.add(d_vnfid + ":input");
            link.setConnectionPointsReference(cp);
            link.setAccess(false);
            link.setExternalAccess(false);
            link.setDhcp(false);

            LinkInstance linkInstance = new LinkInstance(link, "nslink:" + link.getId());

            if (instance.innerlink_list.get(link.getId()) != null)
                add_link(link, linkInstance, endpoint_src, endpoint_target, true, false);
            else
                add_link(link, linkInstance, endpoint_src, endpoint_target, true, true);


            logger.error("ServiceInstanceManager::update_vlink_list: " + action.toString()
                    + " link between " + endpoint_src + " and " + endpoint_target + " failed");

            add_chaining_rules(linkInstance, instance.create_chain, viaPath);


        } else if (action == ACTION_TYPE.DELETE_INSTANCE) {

            String link_name = null;
            String link_id = null;

            for (Map.Entry<String, Map<String, LinkInstance>> link_m : instance.innerlink_list.entrySet()) {
                for (Map.Entry<String, LinkInstance> link : link_m.getValue().entrySet()) {

                    Object[] listt = link.getValue().interfaceList.entrySet().toArray();

                    if ((((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name.equals(endpoint_src.split("_")[1]) &&
                            ((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey().name.equals(endpoint_target.split("_")[1])) ||
                            (((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey().name.equals(endpoint_src.split("_")[1]) &&
                                    ((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name.equals(endpoint_target.split("_")[1]))) {
                        FunctionInstance src;
                        FunctionInstance target;

                        if (((HashMap.Entry<FunctionInstance, String>) listt[0]).getValue().contains("output") &&
                                ((HashMap.Entry<FunctionInstance, String>) listt[1]).getValue().contains("input")) {
                            src = ((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey();
                            target = ((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey();
                        } else {
                            src = ((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey();
                            target = ((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey();
                        }

                        link_name = link.getKey();
                        break;

                    }
                }
                if (link_name != null) {
                    link_id = link_m.getKey();
                    delete_chaining_rules(link_id, link_name);
                    instance.innerlink_list.get(link_id).remove(link_name);
                    break;
                }
            }
            if (link_id == null) {
                for (Map.Entry<String, Map<String, LinkInstance>> link_m : instance.outerlink_list.entrySet()) {
                    for (Map.Entry<String, LinkInstance> link : link_m.getValue().entrySet()) {

                        Object[] listt = link.getValue().interfaceList.entrySet().toArray();

                        if ((((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name.equals(endpoint_src.split("_")[1]) &&
                                ((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey().name.equals(endpoint_target.split("_")[1])) ||
                                (((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey().name.equals(endpoint_src.split("_")[1]) &&
                                        ((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name.equals(endpoint_target.split("_")[1]))) {

                            link_name = link.getKey();
                            break;

                        }
                    }
                    if (link_name != null) {
                        link_id = link_m.getKey();
                        instance.outerlink_list.get(link_id).remove(link_name);
                        break;
                    }
                }
            }

            if (link_name == null) {
                logger.error("ServiceInstanceManager::update_vlink_list: " + action.toString()
                        + " link between " + endpoint_src + " and " + endpoint_target + " failed");
            }


            /*
            for (Map.Entry<String, LinkInstance> link : instance.innerLinks.entrySet()) {

                Object[] listt = link.getValue().interfaceList.entrySet().toArray();

                if ((((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name.equals(endpoint_src.split("_")[1]) &&
                        ((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey().name.equals(endpoint_target.split("_")[1])) ||
                        (((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey().name.equals(endpoint_src.split("_")[1]) &&
                                ((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name.equals(endpoint_target.split("_")[1]))) {
                    FunctionInstance src;
                    FunctionInstance target;

                    if (((HashMap.Entry<FunctionInstance, String>) listt[0]).getValue().contains("output") &&
                            ((HashMap.Entry<FunctionInstance, String>) listt[1]).getValue().contains("input")) {
                        src = ((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey();
                        target = ((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey();
                    } else {
                        src = ((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey();
                        target = ((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey();
                    }

                    link_name = link.getValue().name.split(":")[1];
                    break;

                }
            }*/


        }

        logger.info("ServiceInstanceManager::update_vlink_list: " + action.toString()
                + " link between " + endpoint_src + " and " + endpoint_target + " successful");

        return this.instance;
    }



}
