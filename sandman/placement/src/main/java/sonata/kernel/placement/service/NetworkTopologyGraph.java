package sonata.kernel.placement.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.log4j.Logger;
import sonata.kernel.placement.config.PlacementConfigLoader;
import sonata.kernel.placement.config.PlacementConfig;
import sonata.kernel.placement.config.PopResource;
import sonata.kernel.placement.net.TranslatorTopo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class NetworkTopologyGraph {
    final static Logger logger = Logger.getLogger(NetworkTopologyGraph.class);
    private final Map<String, NetworkNode> m_nodes;
    private final PlacementConfig config;

    public NetworkTopologyGraph()
    {
        m_nodes = new HashMap<String, NetworkNode>();
        config = PlacementConfigLoader.loadPlacementConfig();

        NetworkNode root = new NetworkNode();
        root.set_name("dummy");
        root.set_label("dummy");
        m_nodes.put("dummy", root);

    }

    public NetworkNode generate_graph()
    {
        logger.debug("NetworkTopologyGraph::generate_graph ENTER");
        TranslatorTopo topology_handler = new TranslatorTopo();
        PopResource resource = config.getResources().get(0);
        NetworkTopology_J topology_j = topology_handler.get_topology(resource.getChainingEndpoint());

        if(topology_j == null)
        {
            logger.error("NetworkTopologyGraph::generate_graph: No topology graph found!");
            logger.debug("NetworkTopologyGraph::generate_graph EXIT");
            return null;
        }

        for(NetworkNode_J nodes_j : topology_j.data_centers_switches)
        {
            if(nodes_j.type.equals("Switch") || nodes_j.type.equals("Datacenter")) {
                NetworkNode node = new NetworkNode();
                node.set_name(nodes_j.name);

                if(nodes_j.type.equals("Datacenter"))
                    node.set_label(nodes_j.label);
                else
                    node.set_label(nodes_j.name);

                m_nodes.get("dummy").set_output_links(new NetworkLink(0,0,0,0,node));
                node.set_input_links(new NetworkLink(0,0,0,0, m_nodes.get("dummy")));

                m_nodes.put(node.get_name(), node);

            } else {
                logger.error("NetworkTopologyGraph::generate_graph: Unknown node type in topology graph: " + nodes_j.name + "/" + nodes_j.label);
                continue;
            }
        }

        for(NetworkNode_J nodes_j : topology_j.data_centers_switches)
        {
            NetworkNode node = m_nodes.get(nodes_j.name);

            for(HashMap<String, LinkProperty_J> link : nodes_j.links)
            {
                for(Map.Entry<String, LinkProperty_J> entry : link.entrySet())
                {
                    LinkProperty_J property = entry.getValue();
                    node.set_output_links(new NetworkLink(Float.parseFloat(property.getLoss()),
                            Float.parseFloat(property.getDelay()),
                            Float.parseFloat(property.getJitter()),
                            Float.parseFloat(property.getBandwidth()),
                            m_nodes.get(property.name)));
                    m_nodes.get(property.name).set_input_links(new NetworkLink(Integer.parseInt(property.getLoss()),
                            Float.parseFloat(property.getDelay()),
                            Float.parseFloat(property.getJitter()),
                            Float.parseFloat(property.getBandwidth()),
                            node));
                }
            }

        }

        logger.debug("NetworkTopologyGraph::generate_graph EXIT");
        return m_nodes.get("dummy");
    }

    public static class NetworkNode_J
    {
        public ArrayList<HashMap<String, LinkProperty_J>> getLinks() {
            return links;
        }

        public void setLinks(ArrayList<HashMap<String, LinkProperty_J>> links) {
            this.links = links;

        }

        @JsonProperty("name")
        private String name;
        @JsonProperty("type")
        private String type;
        @JsonProperty("links")
        private ArrayList<HashMap<String, LinkProperty_J>> links;
        @JsonProperty("label")
        private String label;
    }

    public static class LinkProperty_J
    {
        @JsonProperty("name")
        private String name;
        @JsonProperty("loss")
        private String loss;
        @JsonProperty("src_port_name")
        private String source_port;
        @JsonProperty("src_port_id")
        private String source_port_id;
        @JsonProperty("src_port_nr")
        private String source_port_number;
        @JsonProperty("delay")
        private String delay;
        @JsonProperty("bw")
        private String bandwidth;
        @JsonProperty("dst_port_name")
        private String destination_port_name;
        @JsonProperty("jitter")
        private String jitter;
        @JsonProperty("dst_port_id")
        private String destination_port_id;
        @JsonProperty("dst_port_nr")
        private String destination_port_number;

        public String getLoss() {
            if (this.loss == null)
                return "0";
            return loss;
        }
        public String getDelay(){
            if(this.delay == null)
                return "0";
            return delay;
        }
        public String getBandwidth(){
            if(this.bandwidth == null)
                return "0";
            return bandwidth;
        }
        public String getJitter(){
            if(this.jitter == null)
                return "0";
            return jitter;
        }
    }

    public static class NetworkTopology_J
    {
        @JsonProperty("nodes")
        private ArrayList<NetworkNode_J> data_centers_switches;

        public ArrayList<NetworkNode_J> getData_centers_switches() {
            return data_centers_switches;
        }

        public void setData_centers_switches(ArrayList<NetworkNode_J> data_centers_switches) {
            this.data_centers_switches = data_centers_switches;
        }
    }

}

class NetworkLink
{
    final static Logger logger = Logger.getLogger(NetworkLink.class);

    private float loss;
    private float delay;
    private float jitter;
    private float bandwidth;
    private NetworkNode remote_node;

    public NetworkLink(float loss, float delay, float jitter, float bandwidth, NetworkNode remote)
    {
        this.loss = loss;
        this.delay = delay;
        this.jitter = jitter;
        this.bandwidth = bandwidth;
        this.remote_node = remote;
    }

    public float get_loss() {
        return loss;
    }

    public float get_delay() {
        return delay;
    }

    public float get_jitter() {
        return jitter;
    }

    public float get_bandwidth() {
        return bandwidth;
    }

    public NetworkNode get_remote_node()
    {
        return remote_node;
    }

}

class NetworkNode
{
    final static Logger logger = Logger.getLogger(NetworkNode.class);

    private String node_name;
    private String label;
    private List<NetworkLink> output_links = new ArrayList<NetworkLink>();
    private List<NetworkLink> input_links = new ArrayList<NetworkLink>();
    private boolean is_switch = false;

    public String get_name()
    {
        return node_name;
    }

    public String get_label()
    {
        return label;
    }

    public void set_label(String label)
    {
        this.label = label;
    }

    public void set_name(String name)
    {
        this.node_name = name;
    }
    public List<NetworkLink> get_output_links() {
        return output_links;
    }

    public void set_output_links(NetworkLink output_links) {
        this.output_links.add(output_links);
    }

    public List<NetworkLink> get_input_links() {
        return input_links;
    }

    public void set_input_links(NetworkLink input_links) {
        this.input_links.add(input_links);
    }

    public boolean is_switch() {
        return is_switch;
    }

    public void set_is_switch(boolean is_switch) {
        this.is_switch = is_switch;
    }

}
