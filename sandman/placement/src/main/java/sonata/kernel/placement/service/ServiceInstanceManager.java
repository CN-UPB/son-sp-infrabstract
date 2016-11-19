package sonata.kernel.placement.service;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Service;
import org.apache.commons.chain.web.MapEntry;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jaxen.Function;
import org.openstack4j.model.identity.v2.ServiceEndpoint;
import sonata.kernel.VimAdaptor.commons.DeployServiceData;
import sonata.kernel.VimAdaptor.commons.nsd.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.vnfd.Network;
import sonata.kernel.VimAdaptor.commons.vnfd.VnfDescriptor;
import sonata.kernel.VimAdaptor.commons.vnfd.VnfVirtualLink;
import sonata.kernel.placement.Catalogue;

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

    public ServiceInstance initialize_service_instance(DeployServiceData service_data) {
        ServiceDescriptor service = service_data.getNsd();

        instance = new ServiceInstance();
        instance.service = service;


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

            FunctionInstance function_instance = new FunctionInstance(function, descriptor, function.getVnfId());

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

            function_instance.setName(function.getVnfName().split("-")[0] + id);

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
            if (is_outerlink)
                f_instance.links.put(link.getId(), linkInstance);

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

                instance.create_chain.add(new ImmutablePair<String, String>("x", "y"));

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
    public ServiceInstance update_functions_list(String vnf_id, String vnf_name, ACTION_TYPE action) {

        if (action == ACTION_TYPE.ADD_INSTANCE) {

            if (instance.function_list.get(vnf_id) == null) {
            /*
            Special network functions: Load balancers etc.
             */
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
                        n_function.getVnfName().split("-")[0] + id);

                initialize_vnfvlink_list(function_instance, descriptor);

                Map<String, FunctionInstance> map = new HashMap<String, FunctionInstance>();
                map.put(n_function.getVnfId() + id, function_instance);
                instance.function_list.put(n_function.getVnfId(), map);

            } else {

                VnfDescriptor descriptor = nw_function_desc_map.get(network_functions_db.get(vnf_id).getVnfName());
                assert descriptor != null : "Virtual Network Function " + vnf_id + " not found";

                NetworkFunction n_function = network_functions_db.get(vnf_id);


                int id = instance.vnf_uid.get(n_function.getVnfId()).addAndGet(1);
                instance.vnf_uid.get(n_function.getVnfId()).set(id);
                FunctionInstance function_instance = new FunctionInstance(n_function, descriptor,
                        n_function.getVnfName().split("-")[0] + id);

                initialize_vnfvlink_list(function_instance, descriptor);

                instance.function_list.get(n_function.getVnfId()).put(n_function.getVnfId() +
                        id, function_instance);

            }
        } else if (action == ACTION_TYPE.DELETE_INSTANCE) {
            if (instance.function_list.get(vnf_id) == null) {
                logger.error("Virtual Network Function " + vnf_id + " not found");
            } else {
                int id = instance.vnf_uid.get(vnf_id).decrementAndGet();
                instance.vnf_uid.get(vnf_id).set(id);
                instance.function_list.get(vnf_id).remove(vnf_name);
            }
        }
        return instance;

    }

    public ServiceInstance update_vlink_list(String s_vnfid, String d_vnfid, String endpoint_src, String endpoint_target, ACTION_TYPE action) {

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
