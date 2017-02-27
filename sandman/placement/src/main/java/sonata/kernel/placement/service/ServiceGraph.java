package sonata.kernel.placement.service;

import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.nsd.ConnectionPoint;
import sonata.kernel.VimAdaptor.commons.nsd.ForwardingGraph;
import sonata.kernel.VimAdaptor.commons.nsd.NetworkForwardingPath;
import sonata.kernel.VimAdaptor.commons.vnfd.ConnectionPointReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 Helper class to determine the current deployed service graph.
 */
public class ServiceGraph {
    final static Logger logger = Logger.getLogger(ComputeMetrics.class);

    public final ServiceInstance s_instance;
    private final Map<String, Node> m_nodes;
    private final Map<String, Map<String, Node>> f_graph;
    private final Map<String, Node> m_vnf_node;

    //Initialize with service instance.
    public ServiceGraph(ServiceInstance instance) {
        this.s_instance = instance;
        m_nodes = new HashMap<String, Node>();
        f_graph = new HashMap<String, Map<String, Node>>();
        m_vnf_node = new HashMap<String, Node>();
        initialize();
    }

    /*
     Initialize the internal variables.
     */
    private void initialize() {
        logger.debug("ServiceGraph::initialize ENTER");
        for (Map.Entry<String, Map<String, FunctionInstance>> finst_list : s_instance.function_list.entrySet()) {
            for (Map.Entry<String, FunctionInstance> finst : finst_list.getValue().entrySet()) {
                Node node = new Node();
                node.set_instance_name(finst.getValue().name);
                node.set_vnf_id(finst.getValue().function.getVnfId());
                node.set_vnf_name(finst.getValue().function.getVnfName());
                m_nodes.put(finst.getValue().name, node);
            }
        }

        for(ConnectionPoint p : s_instance.service.getConnectionPoints())
        {
            logger.debug("ServiceGraph::initialize: Adding connection point " + p.getId());
            Node node = new Node();
            node.set_instance_name(p.getId());
            node.set_vnf_id(p.getId());
            node.set_vnf_name(p.getId());
            node.is_service_endpoint = true;
            m_nodes.put(p.getId(), node);
        }


        //Initialise the forwarding graph/path
        for(ForwardingGraph ff : s_instance.service.getForwardingGraphs())
        {
            Map<String, Node> f_path_m = new HashMap<String, Node>();
            for(NetworkForwardingPath np : ff.getNetworkForwardingPaths())
            {
                Node root = null;
                Node temp = null;
                for(ConnectionPointReference ref : np.getConnectionPoints())
                {
                    Node node = new Node();
                    node.set_instance_name(ref.getConnectionPointRef());
                    if(null == root) {
                        root = node;
                    }
                    if(null != temp) {
                        temp.add_output_link(node);
                        node.add_input_link(temp);
                    }
                    m_vnf_node.put(ref.getConnectionPointRef(), node);
                    temp = node;
                }
                f_path_m.put(np.getFpId(), root);

            }
            f_graph.put(ff.getFgId(), f_path_m);
        }
        logger.debug("ServiceGraph::initialize EXIT");
        return;

    }

    /*
     Method returns the initial service graph (non-deployed) based on the forwarding graph/path identifier.
     */
    public Node get_forwarding_path(String f_graph_id, String f_path_id)
    {
        logger.debug("ServiceGraph::get_forwarding_path ENTER");
        logger.info("ServiceGraph::get_forwarding_path: Processing forwarding path request for : " + f_graph_id + "(" + f_path_id + ")");
        if(f_graph.get(f_graph_id) == null)
            logger.error("ServiceGraph::get_forwarding_path: Forwarding path: " + f_graph_id + " not found");
        if(f_graph.get(f_graph_id).get(f_path_id) == null)
            logger.error("ServiceGraph::get_forwarding_path: Forwarding path: " + f_graph_id+ "(" + f_path_id + ") not found");

        logger.debug("ServiceGraph::get_forwarding_path EXIT");
        return f_graph.get(f_graph_id).get(f_path_id);

    }

    /*
     Method returns the service graph associated with the current service instance.
     */
    public Node generate_graph() {
        logger.debug("ServiceGraph::generate_graph ENTER");
        for (Map.Entry<String, Map<String, LinkInstance>> link_m : s_instance.outerlink_list.entrySet()) {

            for (LinkInstance link : link_m.getValue().values()) {

                //Management links are not considered in the service graph.
                if(link.isMgmtLink())
                    continue;

                Object[] listt = link.interfaceList.entrySet().toArray();

                if(((HashMap.Entry<FunctionInstance, String>) listt[0]).getValue().contains("input")) {
                    Node ns_conn_p = m_nodes.get(m_vnf_node.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getValue())
                            .get_input_links().get(0).get_instance_name());

                    ns_conn_p.add_output_link(m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name));
                    m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name).add_input_link(ns_conn_p);
                }
                else if(((HashMap.Entry<FunctionInstance, String>) listt[0]).getValue().contains("output")) {
                    Node ns_conn_p = m_nodes.get(m_vnf_node.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getValue())
                            .get_output_links().get(0).get_instance_name());

                    ns_conn_p.add_input_link(m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name));
                    m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name).add_output_link(ns_conn_p);
                }

            }

        }

        for (Map.Entry<String, Map<String, LinkInstance>> link_m : s_instance.innerlink_list.entrySet()) {

            for (Map.Entry<String, LinkInstance> link : link_m.getValue().entrySet()) {
                Object[] listt = link.getValue().interfaceList.entrySet().toArray();

                //Not an inner link.
                if(listt.length < 2)
                    continue;

                if(((HashMap.Entry<FunctionInstance, String>) listt[0]).getValue().contains("output") &&
                        ((HashMap.Entry<FunctionInstance, String>) listt[1]).getValue().contains("input")) {
                    m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name).add_output_link(m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey().name));
                    m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey().name).add_input_link(m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name));
                } else if(((HashMap.Entry<FunctionInstance, String>) listt[0]).getValue().contains("input") &&
                        ((HashMap.Entry<FunctionInstance, String>) listt[1]).getValue().contains("output")) {
                    m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey().name).add_output_link(m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name));
                    m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name).add_input_link(m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[1]).getKey().name));
                } else { //Handle only connection points input/output/mgmt.
                    logger.error("ServiceGraph::generate_graph: Unidentified linkage");
                }

            }
        }
        logger.debug("ServiceGraph::generate_graph EXIT");
        return m_nodes.get("ns:input");
    }
}

/*
 Class for VNF instance or external connection points.
 */
class Node {
    public String get_instance_name() {
        return vnf_instance_name;
    }

    public void set_instance_name(String name) {
        this.vnf_instance_name = name;
    }

    public List<Node> get_output_links() {
        return output_links;
    }

    public void add_output_link(Node to) {
        output_links.add(to);
    }

    public List<Node> get_input_links() {
        return input_links;
    }

    public void add_input_link(Node from) {
        input_links.add(from);
    }

    public List<Node> get_mgmt_links() {
        return mgmt_links;
    }

    public void add_mgmt_link(Node to) {
        mgmt_links.add(to);
    }

    public boolean is_service_endpoint() {
        return is_service_endpoint;
    }

    public String get_vnf_id() {
        return this.vnf_id;
    }

    public void set_vnf_id(String vnf_id) {
        this.vnf_id = vnf_id;
    }

    public String get_vnf_name() {
        return this.vnf_name;
    }

    public void set_vnf_name(String vnf_name) {
        this.vnf_name = vnf_name;
    }

    public String get_data_center() {
        return this.data_center;
    }

    public void set_data_center(String data_center) {
        this.data_center = data_center;
    }

    //VNF instance details.
    private String vnf_instance_name;
    private String vnf_id;
    private String vnf_name;

    //The datacenter on which the vnf instance is deployed.
    private String data_center;

    //List of egress links from this vnf instance
    private List<Node> output_links = new ArrayList<Node>();

    //List of ingress links to this vnf instance
    private List<Node> input_links = new ArrayList<Node>();

    private List<Node> mgmt_links = new ArrayList<Node>();

    //is_service_endpoint = false : ns:input/ns:output
    //is_service_endpoint = true : vnf
    boolean is_service_endpoint = false;
}

