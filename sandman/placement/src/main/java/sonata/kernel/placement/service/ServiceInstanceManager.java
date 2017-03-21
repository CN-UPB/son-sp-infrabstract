package sonata.kernel.placement.service;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import sonata.kernel.VimAdaptor.commons.DeployServiceData;
import sonata.kernel.VimAdaptor.commons.nsd.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.vnfd.VnfDescriptor;
import sonata.kernel.VimAdaptor.commons.vnfd.VnfVirtualLink;
import sonata.kernel.placement.Catalogue;
import sonata.kernel.placement.DatacenterManager;
import sonata.kernel.placement.config.PlacementConfigLoader;
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

    //Returns the currently initialized service instance with the service instance manager.
    public ServiceInstance get_instance() {
        return instance;
    }

    //Set a service instance with instance manager.
    public void set_instance(ServiceInstance instance) {
        this.instance = instance;
    }

    private ServiceInstance instance;
    private String default_pop;
    private PlacementConfig config;

    public ServiceInstanceManager() {
        config = PlacementConfigLoader.loadPlacementConfig();
        default_pop = config.getResources().get(0).getPopName();
    }

    /*
     Method perform the generates an initial service graph out of the SONATA descriptor.
     returns a servie instance object.
     */
    public ServiceInstance initialize_service_instance(DeployServiceData service_data) {
        ServiceDescriptor service = service_data.getNsd();

        instance = new ServiceInstance();
        instance.service = service;

        String service_name = service.getName();
        ArrayList<ConnectionPoint> connection_points = Lists.newArrayList(service.getConnectionPoints());
        ArrayList<ForwardingGraph> forwarding_graph = Lists.newArrayList(service.getForwardingGraphs());

        //Initialize the vnf instances in the graph
        initialize_function_instance(service_data);

        //Initialize the links between the vnf instances in the graph.
        initialize_vlinks_list(service_data);

        //Return the instance of the service instance.
        return instance;


    }

    /*
     Method initializes initial service graph vnf instances.
     */
    protected void initialize_function_instance(DeployServiceData service_data) {
        instance.nw_function_desc_map = new HashMap<String, VnfDescriptor>();
        instance.network_functions_db = new HashMap<String, NetworkFunction>();
        ArrayList<NetworkFunction> network_functions = Lists.newArrayList(instance.service.getNetworkFunctions());

        for (VnfDescriptor descriptor : service_data.getVnfdList()) {
            instance.nw_function_desc_map.put(descriptor.getName(), descriptor);
            logger.debug("VNF Descriptor " + descriptor);
        }

        for (NetworkFunction function : network_functions) {

            //Get an available vnf instance name from the pool of names associated with the vnf id.
            String vnf_instance_name = get_next_vnf_name(function.getVnfId(), instance.vnf_uid_s);

            instance.network_functions_db.put(function.getVnfId(), function);
            VnfDescriptor descriptor = instance.nw_function_desc_map.get(function.getVnfName());
            assert descriptor != null : "Virtual Network Function " + function.getVnfName() + " not found";

            FunctionInstance function_instance = new FunctionInstance(function, descriptor, function.getVnfId(), default_pop);

            //Allocate resources for the vnf instances
            boolean resource_status = consume_resources(function_instance, function_instance.data_center);
            if (!resource_status) //Ignore vnf instance addition. Insufficient resources.
                continue;


            int id;


            if (null == instance.function_list.get(function.getVnfId())) {
                AtomicInteger vnf_uid = new AtomicInteger(0);
                id = vnf_uid.addAndGet(1);
                vnf_uid.set(id);
                instance.vnf_uid.put(function.getVnfId(), vnf_uid);
                Map<String, FunctionInstance> map = new HashMap<String, FunctionInstance>();

                map.put(vnf_instance_name, function_instance);
                instance.function_list.put(function.getVnfId(), map);


            } else {

                id = instance.vnf_uid.get(function.getVnfId()).addAndGet(1);
                instance.vnf_uid.get(function.getVnfId()).set(id);

                instance.function_list.get(function.getVnfId()).put(vnf_instance_name, function_instance);

            }

            function_instance.setName(vnf_instance_name.split("_")[1]);

            initialize_vnfvlink_list(function_instance, descriptor);

        }
    }

    /*
     Method initializes initial service graph links.
     */
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

                linkInstance.interfaceList.put(f_instance, ref);
            }
            if (is_outerlink) {
                f_instance.links.put(link.getId(), linkInstance);
            }

        }
    }

    /*
     Method adds a new virtual link between two vnf instances in the service graph.
     */
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

        //Links creation during scale out.
        //Build input links as indicated by flag build_in
        linkInstance.setBuild_in(build_in);

        //Build output links as indicated by flag build_out
        linkInstance.setBuild_out(build_out);

        int id;

        //Get a available link name from the pool of available link names associated with the link id.
        String link_name = get_next_link_name(link.getId(), instance.vnf_vlinkid_s);
        if (is_nslink) { //Handle external ns links
            if (instance.outerlink_list.get(link.getId()) == null) {
                AtomicInteger vnf_vlinkid = new AtomicInteger(0);
                id = vnf_vlinkid.addAndGet(1);
                vnf_vlinkid.set(id);
                instance.vnf_vlinkid.put(link.getId(), vnf_vlinkid);
                Map<String, LinkInstance> map = new HashMap<String, LinkInstance>();

                map.put(link_name, linkInstance);
                instance.outerlink_list.put(link.getId(), map);
            } else {
                id = instance.vnf_vlinkid.get(link.getId()).addAndGet(1);
                instance.vnf_vlinkid.get(link.getId()).set(id);

                instance.outerlink_list.get(link.getId()).put(link_name, linkInstance);
            }
            instance.outerLinks.put(link.getId(), linkInstance);

        } else { //Handle internal virtual links between vnf instance.
            if (instance.innerlink_list.get(link.getId()) == null) {
                AtomicInteger vnf_vlinkid = new AtomicInteger(0);
                id = vnf_vlinkid.addAndGet(1);
                vnf_vlinkid.set(id);
                instance.vnf_vlinkid.put(link.getId(), vnf_vlinkid);
                Map<String, LinkInstance> map = new HashMap<String, LinkInstance>();

                map.put(link_name, linkInstance);
                instance.innerlink_list.put(link.getId(), map);
            } else {
                id = instance.vnf_vlinkid.get(link.getId()).addAndGet(1);
                instance.vnf_vlinkid.get(link.getId()).set(id);

                instance.innerlink_list.get(link.getId()).put(link_name, linkInstance);
            }

        }
        return;
    }

    /*
      Method adds management links for the vnf instance.
     */
    protected void internal_add_mgmt_link(FunctionInstance f_inst) {

        logger.debug("Add internal mgmt link " + f_inst.getName());
        VirtualLink link = new VirtualLink();
        link.setId("mgmt");
        LinkInstance linkInstance = new LinkInstance(link, "nslink:mgmt");
        linkInstance.interfaceList.put(f_inst, f_inst.function.getVnfId() + ":mgmt");

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

    /*
     Method handles link addition during scale out.
     */
    protected void update_ns_link(FunctionInstance f_inst) {
        ArrayList<VirtualLink> virtual_links = Lists.newArrayList(instance.service.getVirtualLinks());

        for (VirtualLink link : virtual_links) {
            LinkInstance linkInstance = new LinkInstance(link, "nslink:" + link.getId());

            boolean is_nslink = false;

            for (String cp_ref : link.getConnectionPointsReference()) {
                String[] cp_ref_str = cp_ref.split(":");
                assert cp_ref_str != null && cp_ref_str.length == 2 : "Virtual Link " + link.getId() + " uses odd vnf reference " + cp_ref;
                String vnfid = cp_ref_str[0];

                if (vnfid.equals("ns"))
                    is_nslink = true;
            }

            if (!is_nslink)
                continue;

            if (linkInstance.isMgmtLink())
                continue;

            for (String cp_ref : link.getConnectionPointsReference()) {
                System.out.println("update_ns_link " + link.getId() + "  " + cp_ref);
                String[] cp_ref_str = cp_ref.split(":");
                assert cp_ref_str != null && cp_ref_str.length == 2 : "Virtual Link " + link.getId() + " uses odd vnf reference " + cp_ref;
                String vnfid = cp_ref_str[0];
                String connectionPointName = cp_ref_str[1];

                if (f_inst.function.getVnfId().equals(vnfid) == true) {
                    LinkInstance vnf_LinkInstance = f_inst.links.get(connectionPointName);
                    assert vnf_LinkInstance != null : "In Service " + instance.service.getName() + " Virtual Link "
                            + link.getId() + " connects to function " + f_inst.name
                            + " that does not contain link for connection point " + connectionPointName;
                    linkInstance.interfaceList.put(f_inst, cp_ref);

                    int id;
                    String link_name = get_next_link_name(link.getId(), instance.vnf_vlinkid_s);
                    if (instance.outerlink_list.get(link.getId()) == null) {
                        AtomicInteger vnf_vlinkid = new AtomicInteger(0);
                        id = vnf_vlinkid.addAndGet(1);
                        vnf_vlinkid.set(id);
                        instance.vnf_vlinkid.put(link.getId(), vnf_vlinkid);
                        Map<String, LinkInstance> map = new HashMap<String, LinkInstance>();

                        map.put(link_name, linkInstance);
                        instance.outerlink_list.put(link.getId(), map);

                    } else {
                        id = instance.vnf_vlinkid.get(link.getId()).addAndGet(1);
                        instance.vnf_vlinkid.get(link.getId()).set(id);

                        instance.outerlink_list.get(link.getId()).put(link_name, linkInstance);
                    }
                    //Create rules for loadbalancing at the ns:input.
                    if (connectionPointName.equals("input")) {
                        add_input_lb_rules(linkInstance, instance.create_input_lb_links);
                    }

                }

            }

        }
        return;

    }

    /*
     Method deletes an external link.
     */
    protected void delete_ns_link(String f_inst) {
        String key = "";
        for (Map.Entry<String, Map<String, LinkInstance>> link_ll : instance.outerlink_list.entrySet()) {
            for (Map.Entry<String, LinkInstance> link_e : link_ll.getValue().entrySet()) {
                for (Map.Entry<FunctionInstance, String> finst : link_e.getValue().interfaceList.entrySet()) {
                    if (finst.getKey().name.equals(f_inst)) {
                        key = link_e.getKey();
                    }
                }
            }
        }

        //Release the link name back to the names pool.
        String key_name = key.substring(0, key.lastIndexOf(":"));
        instance.outerlink_list.get(key_name).remove(key);
        release_link_name(key_name, key, instance.vnf_vlinkid_s);

        return;
    }

    /*
     Method initializes the links instance in the service graph
     */
    protected void initialize_link(VirtualLink link, LinkInstance linkInstance) {
        boolean is_nslink = false;
        boolean is_inputlink = false;

        for (String cp_ref : link.getConnectionPointsReference()) {

            String[] cp_ref_str = cp_ref.split(":");
            assert cp_ref_str != null && cp_ref_str.length == 2 : "Virtual Link " + link.getId() + " uses odd vnf reference " + cp_ref;
            String vnfid = cp_ref_str[0];
            String connectionPointName = cp_ref_str[1];

            if ("ns".equals(vnfid)) {
                is_nslink = true;
                continue;
            }

            if (connectionPointName.equals("input"))
                is_inputlink = true;

            Map<String, FunctionInstance> vnf_instances = instance.function_list.get(vnfid);
            assert vnf_instances.size() != 0 : "In Service " + instance.service.getName() + " Virtual Link " + link.getId() + " references unknown vnf with id " + vnfid;

            for (Map.Entry<String, FunctionInstance> finst : vnf_instances.entrySet()) {
                LinkInstance vnfLinkInstance = finst.getValue().links.get(connectionPointName);
                assert vnfLinkInstance != null : "In Service " + instance.service.getName() + " Virtual Link "
                        + link.getId() + " connects to function " + finst.getValue().name
                        + " that does not contain link for connection point " + connectionPointName;

                linkInstance.interfaceList.put(finst.getValue(), cp_ref);
            }

        }

        int id;
        if (is_nslink) { //Handle if its a external ns link
            if (instance.outerlink_list.get(link.getId()) == null) {
                AtomicInteger vnf_vlinkid = new AtomicInteger(0);
                id = vnf_vlinkid.addAndGet(1);
                vnf_vlinkid.set(id);
                instance.vnf_vlinkid.put(link.getId(), vnf_vlinkid);

                String link_name = get_next_link_name(link.getId(), instance.vnf_vlinkid_s);
                Map<String, LinkInstance> map = new HashMap<String, LinkInstance>();

                map.put(link_name, linkInstance);
                instance.outerlink_list.put(link.getId(), map);
                if (is_inputlink)
                    add_input_lb_rules(linkInstance, instance.create_input_lb_links);

            } else {
                id = instance.vnf_vlinkid.get(link.getId()).addAndGet(1);
                instance.vnf_vlinkid.get(link.getId()).set(id);

                String link_name = get_next_link_name(link.getId(), instance.vnf_vlinkid_s);

                instance.outerlink_list.get(link.getId()).put(link_name, linkInstance);
            }
            instance.outerLinks.put(link.getId(), linkInstance);

        } else { //Handle it as internal link between vnf instances
            String link_name = get_next_link_name(link.getId(), instance.vnf_vlinkid_s);
            if (instance.innerlink_list.get(link.getId()) == null) {
                AtomicInteger vnf_vlinkid = new AtomicInteger(0);
                id = vnf_vlinkid.addAndGet(1);
                vnf_vlinkid.set(id);
                instance.vnf_vlinkid.put(link.getId(), vnf_vlinkid);

                Map<String, LinkInstance> map = new HashMap<String, LinkInstance>();


                map.put(link_name, linkInstance);
                instance.innerlink_list.put(link.getId(), map);
            } else {
                id = instance.vnf_vlinkid.get(link.getId()).addAndGet(1);
                instance.vnf_vlinkid.get(link.getId()).set(id);

                instance.innerlink_list.get(link.getId()).put(link_name, linkInstance);

            }

        }
        //Add chaining rules.
        add_chaining_rules(linkInstance, instance.create_chain, null);


        return;
    }

    /*
     Method clears all the rules for chaining and loadbalancing.
     */
    public void flush_chaining_rules() {
        instance.create_chain.clear();
        instance.delete_chain.clear();
        instance.customized_chains.clear();
        instance.create_input_lb_links.clear();
        instance.delete_input_lb_links.clear();
    }

    /*
     Method adds a loadbalancing rule.
     */
    protected void add_input_lb_rules(LinkInstance linkInstance, List<Pair<String, String>> chain) {
        Object[] finst_t = linkInstance.interfaceList.entrySet().toArray();

        String server = "";
        String port = "";
        String intf = "";


        for (Object v_link : (((HashMap.Entry<FunctionInstance, String>) finst_t[0]).getKey().descriptor.getVirtualLinks()).toArray()) {
            if (((VnfVirtualLink) v_link).getId().equals("input")) {
                for (String if_name : ((VnfVirtualLink) v_link).getConnectionPointsReference()) {
                    if (if_name.contains("vdu")) {
                        intf = if_name.split(":")[1];
                    }
                }
            }
        }
        port = ((HashMap.Entry<FunctionInstance, String>) finst_t[0]).getKey().getName() + ":" + intf
                + ":" + ((HashMap.Entry<FunctionInstance, String>) finst_t[0]).getValue().split(":")[1];
        server = ((HashMap.Entry<FunctionInstance, String>) finst_t[0]).getKey().getName();

        chain.add(new ImmutablePair<String, String>(server, port));
        return;

    }

    /*
     Method deletes a load balancing rule
     */
    protected void delete_input_lb_rules(FunctionInstance f_inst) {
        String link_name = null;
        String link_id = null;
        LinkInstance l_inst = null;
        boolean link_found = false;

        for (Map.Entry<String, Map<String, LinkInstance>> link_m : instance.outerlink_list.entrySet()) {
            for (Map.Entry<String, LinkInstance> link : link_m.getValue().entrySet()) {

                if (link.getValue().isMgmtLink())
                    break;

                Object[] listt = link.getValue().interfaceList.entrySet().toArray();

                for (int i = 0; i < listt.length; i++) {

                    if (((HashMap.Entry<FunctionInstance, String>) listt[i]).getValue().contains(":input") && ((HashMap.Entry<FunctionInstance, String>) listt[i]).getKey().name.equals(f_inst.name)) {
                        link_name = link.getKey();
                        l_inst = link.getValue();

                        link_found = true;
                        break;
                    }
                }

                if (link_found)
                    break;

            }
            if (link_found && link_name != null) {
                link_id = link_m.getKey();
                add_input_lb_rules(l_inst, instance.delete_input_lb_links);
                break;
            }
        }
    }


    /*
     Method deletes a chaining rule.
     */
    protected void delete_chaining_rules(String link_id, String link_name) {

        LinkInstance linkInstance = instance.innerlink_list.get(link_id).get(link_name);
        add_chaining_rules(linkInstance, instance.delete_chain, null);
        return;
    }

    /*
     Method adds a chaining rule.
     */
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
                    + ":" + ((HashMap.Entry<FunctionInstance, String>) finst_t[i]).getValue().split(":")[1];
            server[i] = ((HashMap.Entry<FunctionInstance, String>) finst_t[i]).getKey().getName();
        }


        if (((HashMap.Entry<FunctionInstance, String>) finst_t[1]).getValue().split(":")[1].equals("input")) {
            chain.add(new ImmutablePair<Pair<String, String>, Pair<String, String>>(new ImmutablePair<String, String>(server[0], port[0]), new ImmutablePair<String, String>(server[1], port[1])));
        } else {
            chain.add(new ImmutablePair<Pair<String, String>, Pair<String, String>>(new ImmutablePair<String, String>(server[1], port[1]), new ImmutablePair<String, String>(server[0], port[0])));
        }

        if (viaPath != null && viaPath.size() != 0) {
            if (((HashMap.Entry<FunctionInstance, String>) finst_t[1]).getValue().split(":")[1].equals("input")) {
                instance.customized_chains.add(new ImmutablePair<Pair<String, String>, List<String>>
                        (new ImmutablePair<String, String>(server[0], server[1]), viaPath));
            } else {
                instance.customized_chains.add(new ImmutablePair<Pair<String, String>, List<String>>
                        (new ImmutablePair<String, String>(server[1], server[0]), viaPath));
            }
            linkInstance.viaPath = viaPath;
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

    /*
     Method tries to fetch vnf descriptors from the Catalog.
     */
    public void update_vnfdescriptor(String vnf_id, boolean new_addition) {
        if (instance.function_list.get(vnf_id) == null && !new_addition) {
            logger.info("ServiceInstanceManager:update_vnfdescriptor ingored for " + vnf_id + ".");
            return;
        } else {
            VnfDescriptor descriptor = Catalogue.internalFunctions.get(vnf_id);
            instance.nw_function_desc_map.put(instance.network_functions_db.get(vnf_id).getVnfName(), descriptor);

            logger.info("ServiceInstanceManager:update_vnfdescriptor updated for " + vnf_id + ". "
                    + "VnfDescriptor: " + descriptor.getName() + " Version: " + descriptor.getVendor());
            return;
        }
    }

    /*
    Method adds/deletes vnf instances into the service graph.
    Note:
    In case of vnf instance addition, vnf_name = null
    In case of vnf instance deletion, vnf_name must be the name associated with the vnf instance.
     */
    public String update_functions_list(String vnf_id, String vnf_name, String PopName, ACTION_TYPE action) {

        if (action == ACTION_TYPE.ADD_INSTANCE) { //Handle vnf instance addition

            if (instance.function_list.get(vnf_id) == null) {
            /*
            Special network functions: Load balancers etc.
             */
                logger.debug("Add instance of unknown function " + vnf_id);
                Catalogue.loadInternalFunctions();
                VnfDescriptor descriptor = Catalogue.internalFunctions.get(vnf_id);
                assert descriptor != null : "Virtual Network Function " + vnf_id + " not found";


                NetworkFunction n_function = new NetworkFunction();
                n_function.setVnfId(vnf_id);
                n_function.setVnfName(descriptor.getName());
                n_function.setVnfVersion(descriptor.getVersion());
                n_function.setVnfVendor(descriptor.getVendor());
                n_function.setDescription(descriptor.getDescription());


                instance.network_functions_db.put(vnf_id, n_function);

                String ss = instance.network_functions_db.get(vnf_id).getVnfName();

                instance.nw_function_desc_map.put(ss, descriptor);

                AtomicInteger vnf_uid = new AtomicInteger(0);
                int id = vnf_uid.addAndGet(1);
                vnf_uid.set(id);
                instance.vnf_uid.put(n_function.getVnfId(), vnf_uid);

                String vnf_instance_name = get_next_vnf_name(n_function.getVnfId(), instance.vnf_uid_s);

                FunctionInstance function_instance = new FunctionInstance(n_function, descriptor,
                        vnf_instance_name.split("_")[1], PopName);

                boolean resource_status = consume_resources(function_instance, function_instance.data_center);
                if (!resource_status)
                    return null;

                initialize_vnfvlink_list(function_instance, descriptor);

                Map<String, FunctionInstance> map = new HashMap<String, FunctionInstance>();

                map.put(vnf_instance_name, function_instance);
                instance.function_list.put(n_function.getVnfId(), map);

                internal_add_mgmt_link(function_instance);


                return function_instance.name;

            } else {
                logger.debug("Add instance of known function " + vnf_id);
                VnfDescriptor descriptor = instance.nw_function_desc_map.get(instance.network_functions_db.get(vnf_id).getVnfName());
                assert descriptor != null : "Virtual Network Function " + vnf_id + " not found";

                NetworkFunction n_function = instance.network_functions_db.get(vnf_id);


                int id = instance.vnf_uid.get(n_function.getVnfId()).addAndGet(1);
                instance.vnf_uid.get(n_function.getVnfId()).set(id);

                String vnf_instance_name = get_next_vnf_name(n_function.getVnfId(), instance.vnf_uid_s);


                FunctionInstance function_instance = new FunctionInstance(n_function, descriptor,
                        vnf_instance_name.split("_")[1], PopName);

                boolean resource_status = consume_resources(function_instance, function_instance.data_center);
                if (!resource_status)
                    return null; //Return null if vnf instance could not be instantiated on a datacenter due to insufficient resource.

                //Initialize the connection points associated with the vnf instance
                initialize_vnfvlink_list(function_instance, descriptor);

                //Add external links
                update_ns_link(function_instance);

                //Add management link
                internal_add_mgmt_link(function_instance);

                instance.function_list.get(n_function.getVnfId()).put(vnf_instance_name, function_instance);

                //Return the vnf instance name just created.
                return function_instance.name;

            }
        } else if (action == ACTION_TYPE.DELETE_INSTANCE) { //Handle vnf instance deletion
            logger.debug("Delete instance of function " + vnf_id + " with name " + vnf_name);
            if (instance.function_list.get(vnf_id) == null) {
                logger.error("Virtual Network Function " + vnf_id + " not found");
            } else {

                FunctionInstance f_inst = instance.function_list.get(vnf_id).get(vnf_name);
                //Release all resources
                relinquish_resource(f_inst);

                //Delete internal links
                delete_inner_links(f_inst);

                //Delete load balancing rules.
                delete_input_lb_rules(f_inst);

                //Delete external links
                delete_ns_link((vnf_name.split("_"))[1]);
                int id = instance.vnf_uid.get(vnf_id).decrementAndGet();
                instance.vnf_uid.get(vnf_id).set(id);

                //Release vnf instance name back to the names pool
                release_vnf_name(vnf_name, instance.vnf_uid_s);
                instance.function_list.get(vnf_id).remove(vnf_name);

            }
        }
        return null;

    }

    /*
     Method deletes internal vnf links from a service graph
     */
    protected void delete_inner_links(FunctionInstance f_instance) {

        for (Map.Entry<String, Map<String, LinkInstance>> link_m : instance.innerlink_list.entrySet()) {
            List<String> link_names = new ArrayList<String>();
            for (Map.Entry<String, LinkInstance> entry : link_m.getValue().entrySet()) {
                for (Map.Entry<FunctionInstance, String> ee : entry.getValue().interfaceList.entrySet()) {
                    if (f_instance.name.equals(ee.getKey().name)) {
                        link_names.add(entry.getKey());
                    }
                }
            }

            for (String vlinks : link_names) {
                String vlinks_id = vlinks.substring(0, vlinks.lastIndexOf(":"));
                delete_chaining_rules(vlinks_id, vlinks);
                link_m.getValue().remove(vlinks);
                release_link_name(vlinks_id, vlinks, instance.vnf_vlinkid_s);
            }
        }
    }

    /*
     Method enable addition/deletion of links from a given instance of the service graph.
     */
    public ServiceInstance update_vlink_list(String s_vnfid, String d_vnfid, String endpoint_src, String endpoint_target, List<String> viaPath, ACTION_TYPE action) {

        assert instance.function_list.get(s_vnfid) != null : "Virtual Network Function " + s_vnfid + " not found";
        assert instance.function_list.get(s_vnfid).get(endpoint_src) != null : "Virtual Network Function instance "
                + endpoint_src + " not found";
        assert instance.function_list.get(d_vnfid) != null : "Virtual Network Function" + d_vnfid + " not found";
        assert instance.function_list.get(d_vnfid).get(endpoint_target) != null : "Virtual Network Function instance "
                + endpoint_target + " not found";

        if (action == ACTION_TYPE.ADD_INSTANCE) { //Handle link addition

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
            //Add chaining rules.
            add_chaining_rules(linkInstance, instance.create_chain, viaPath);


        } else if (action == ACTION_TYPE.DELETE_INSTANCE) { //Handle link deletion

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
                    //Delete chaining rules
                    delete_chaining_rules(link_id, link_name);
                    //Release link names associated with the link being deleted.
                    release_link_name(link_id, link_name, instance.vnf_vlinkid_s);
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
                        release_link_name(link_id, link_name, instance.vnf_vlinkid_s);
                        instance.outerlink_list.get(link_id).remove(link_name);
                        break;
                    }
                }
            }

            if (link_name == null) {
                logger.error("ServiceInstanceManager::update_vlink_list: " + action.toString()
                        + " link between " + endpoint_src + " and " + endpoint_target + " failed");
            }
        }

        logger.info("ServiceInstanceManager::update_vlink_list: " + action.toString()
                + " link between " + endpoint_src + " and " + endpoint_target + " successful");

        //In case of link addition return the link instance.
        return this.instance;
    }

    /*
     Method enabled acquiring of required resources from the particular datacenter resource pool
     */
    protected boolean consume_resources(FunctionInstance function_instance, String data_center) {
        double multiplier =
                function_instance.deploymentUnits.get(0).getResourceRequirements().getMemory().getSizeUnit().getMultiplier();
        boolean memory_status = DatacenterManager.consume_memory(data_center,
                function_instance.deploymentUnits.get(0).getResourceRequirements().getMemory().getSize() * multiplier);

        if (memory_status == false) {
            logger.error("ServiceInstanceManager::consume_resources: Insufficient memory resource on "
                    + data_center + ". Required: " + function_instance.deploymentUnits.get(0).getResourceRequirements().getMemory().getSize() * multiplier
                    + " " + function_instance.deploymentUnits.get(0).getResourceRequirements().getMemory().getSizeUnit().name()
                    + ". Available: " + DatacenterManager.get_available_memory(data_center)
                    + " " + function_instance.deploymentUnits.get(0).getResourceRequirements().getMemory().getSizeUnit().name());
            return false;
        }

        boolean cpu_status = DatacenterManager.consume_cpu(data_center,
                function_instance.deploymentUnits.get(0).getResourceRequirements().getCpu().getVcpus());

        if (cpu_status == false) {
            logger.error("ServiceInstanceManager::consume_resources: Insufficient cpu resource on "
                    + data_center + ". Required: " + function_instance.deploymentUnits.get(0).getResourceRequirements().getCpu().getVcpus()
                    + " Available: " + DatacenterManager.get_available_cpu(data_center));
            DatacenterManager.relinquish_memory(data_center,
                    function_instance.deploymentUnits.get(0).getResourceRequirements().getMemory().getSize() * multiplier);
            return false;
        }

        multiplier =
                function_instance.deploymentUnits.get(0).getResourceRequirements().getStorage().getSizeUnit().getMultiplier();
        boolean storage_status = DatacenterManager.consume_storage(data_center,
                function_instance.deploymentUnits.get(0).getResourceRequirements().getStorage().getSize() * multiplier);

        if (storage_status == false) {
            logger.error("ServiceInstanceManager::consume_resources: Insufficient storage resource on "
                    + data_center + ". Required: " + function_instance.deploymentUnits.get(0).getResourceRequirements().getStorage().getSize() * multiplier
                    + " " + function_instance.deploymentUnits.get(0).getResourceRequirements().getStorage().getSizeUnit().name()
                    + " Available: " + DatacenterManager.get_available_storage(data_center)
                    + " " + function_instance.deploymentUnits.get(0).getResourceRequirements().getMemory().getSizeUnit().name());
            DatacenterManager.relinquish_memory(data_center,
                    function_instance.deploymentUnits.get(0).getResourceRequirements().getMemory().getSize() * multiplier);
            DatacenterManager.relinquish_cpu(data_center,
                    function_instance.deploymentUnits.get(0).getResourceRequirements().getCpu().getVcpus());
            return false;

        }

        return true;
    }

    /*
     Method enables release of consumed resource back to the datacenter resource pool
     */
    protected boolean relinquish_resource(FunctionInstance function_instance) {
        double multiplier =
                function_instance.deploymentUnits.get(0).getResourceRequirements().getMemory().getSizeUnit().getMultiplier();
        DatacenterManager.relinquish_memory(function_instance.data_center,
                function_instance.deploymentUnits.get(0).getResourceRequirements().getMemory().getSize() * multiplier);

        multiplier =
                function_instance.deploymentUnits.get(0).getResourceRequirements().getStorage().getSizeUnit().getMultiplier();
        DatacenterManager.relinquish_storage(function_instance.data_center,
                function_instance.deploymentUnits.get(0).getResourceRequirements().getStorage().getSize() * multiplier);

        DatacenterManager.relinquish_cpu(function_instance.data_center,
                function_instance.deploymentUnits.get(0).getResourceRequirements().getCpu().getVcpus());

        return true;

    }

    /*
     Method enables offline migration of vnf instances from one datacenter to another.
     */

    public boolean move_function_instance(String vnf_name, String pop_name) {
        FunctionInstance f_instance = instance.getFunctionInstance(vnf_name);
        if (f_instance == null)
            return false;

        //Check if the new datacenter has the necessary resources to allocate a new vnf instance.
        boolean status = consume_resources(f_instance, pop_name);
        if (false == status)
            return false;

        //Relinquish the currently used resources to the current data center.
        relinquish_resource(f_instance);
        f_instance.data_center = pop_name;

        //
        for (Map.Entry<String, Map<String, LinkInstance>> link_m : instance.innerlink_list.entrySet()) {
            HashMap<String, LinkInstance> links = new HashMap<String, LinkInstance>();
            for (Map.Entry<String, LinkInstance> entry : link_m.getValue().entrySet()) {
                for (Map.Entry<FunctionInstance, String> ee : entry.getValue().interfaceList.entrySet()) {
                    if (f_instance.name.equals(ee.getKey().name)) {
                        links.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            for (Map.Entry<String, LinkInstance> vlinks : links.entrySet()) {
                delete_chaining_rules(vlinks.getValue().getLinkId(), vlinks.getKey());
                add_chaining_rules(vlinks.getValue(), instance.create_chain, vlinks.getValue().viaPath);
            }
        }

        return true;
    }

    /*
     Method returns the next available link name from the pool
     */
    private String get_next_link_name(String link_id, Map<String, Stack<String>> id_map) {
        if (id_map.get(link_id) == null) {
            Stack<String> instance_name = new Stack<String>();
            for (int i = 1000; i >= 1; i--) {
                instance_name.push(link_id + ":" + i);
            }

            id_map.put(link_id, instance_name);
            return instance_name.pop();

        } else {
            return id_map.get(link_id).pop();
        }

    }

    /*
     Method returns the link name back to pool.
     */
    private void release_link_name(String link_id, String link_instance, Map<String, Stack<String>> id_map) {
        if (id_map.get(link_id) == null) //No such link id available in the pool
            return;

        //Release link name back to the pool.
        id_map.get(link_id).add(link_instance);

        return;

    }

    /*
     Method returns the next available vnf instance name from the pool of vnf names for a vnf type.
     */
    private String get_next_vnf_name(String vnf_id, Map<String, Stack<String>> id_map) {
        if (id_map.get(vnf_id) == null) {
            Stack<String> instance_name = new Stack<String>();
            for (int i = 100; i >= 1; i--) {
                instance_name.push(vnf_id + i);
            }

            id_map.put(vnf_id, instance_name);
            return instance_name.pop();

        } else {
            return id_map.get(vnf_id).pop();
        }
    }

    /*
     Method returns the vnf instance name back to the pool
     */
    protected void release_vnf_name(String vnf_instance, Map<String, Stack<String>> id_map) {
        String vnf_id = instance.findVnfIdFromVnfInstanceName(vnf_instance.split("_")[1]);

        if (vnf_id == null) {
            return;
        }

        id_map.get(vnf_id).add(vnf_instance);

        return;
    }


}
