package sonata.kernel.placement.service;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceGraph {
    final static Logger logger = Logger.getLogger(ComputeMetrics.class);

    public final ServiceInstance s_instance;
    private final Map<String, Node> m_nodes;

    public ServiceGraph(ServiceInstance instance) {
        this.s_instance = instance;
        m_nodes = new HashMap<String, Node>();
        initialize();
    }

    private void initialize() {
        logger.debug("ServiceGraph::initialize ENTER");
        for (Map.Entry<String, Map<String, FunctionInstance>> finst_list : s_instance.function_list.entrySet()) {
            for (Map.Entry<String, FunctionInstance> finst : finst_list.getValue().entrySet()) {
                Node node = new Node();
                node.set_name(finst.getValue().name);
                m_nodes.put(finst.getValue().name, node);
            }
        }

        Node ns_input = new Node();
        ns_input.set_name("ns:input");
        ns_input.is_service_endpoint = true;
        m_nodes.put("ns:input", ns_input);

        Node ns_output = new Node();
        ns_output.set_name("ns:output");
        ns_output.is_service_endpoint = true;
        m_nodes.put("ns:output", ns_output);

        Node ns_mgmt = new Node();
        ns_mgmt.set_name("ns:mgmt");
        ns_mgmt.is_service_endpoint = true;
        m_nodes.put("ns:mgmt", ns_mgmt);

        logger.debug("ServiceGraph::initialize EXIT");
        return;

    }

    public Node generate_graph() {
        logger.debug("ServiceGraph::generate_graph ENTER");
        for (Map.Entry<String, Map<String, LinkInstance>> link_m : s_instance.outerlink_list.entrySet()) {

            for (LinkInstance link : link_m.getValue().values()) {

                if(link.isMgmtLink())
                    continue;

                Object[] listt = link.interfaceList.entrySet().toArray();

                if(((HashMap.Entry<FunctionInstance, String>) listt[0]).getValue().contains("input")) {
                    m_nodes.get("ns:input").add_output_link(m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name));
                    m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name).add_input_link(m_nodes.get("ns:input"));
                }
                else if(((HashMap.Entry<FunctionInstance, String>) listt[0]).getValue().contains("output")) {
                    m_nodes.get("ns:output").add_input_link(m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name));
                    m_nodes.get(((HashMap.Entry<FunctionInstance, String>) listt[0]).getKey().name).add_output_link(m_nodes.get("ns:output"));
                }

            }

        }

        for (Map.Entry<String, Map<String, LinkInstance>> link_m : s_instance.innerlink_list.entrySet()) {

            for (Map.Entry<String, LinkInstance> link : link_m.getValue().entrySet()) {
                Object[] listt = link.getValue().interfaceList.entrySet().toArray();

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
                } else {
                    logger.error("ServiceGraph::generate_graph: Unidentified linkage");
                }

            }
        }
        logger.debug("ServiceGraph::generate_graph EXIT");
        return m_nodes.get("ns:input");
    }
}

class Node {
    public String get_name() {
        return name;
    }

    public void set_name(String name) {
        this.name = name;
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

    String name;

    List<Node> output_links = new ArrayList<Node>();
    List<Node> input_links = new ArrayList<Node>();
    List<Node> mgmt_links = new ArrayList<Node>();

    boolean is_service_endpoint = false;
}

