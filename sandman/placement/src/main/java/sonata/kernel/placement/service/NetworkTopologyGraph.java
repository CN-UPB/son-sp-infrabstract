package sonata.kernel.placement.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class NetworkTopologyGraph {
    final static Logger logger = Logger.getLogger(NetworkTopologyGraph.class);
    private final Map<String, NetworkNode> m_nodes;
    private String json_graph;

    public NetworkTopologyGraph(String graph)
    {
        m_nodes = new HashMap<String, NetworkNode>();
        json_graph = graph;

        int a = -1;
        a = Integer.parseInt("10");

    }

    public NetworkNode generate_graph()
    {

        return null;
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
    }

    public static class NodeAliasMap_J
    {
        @JsonProperty("name")
        String name;
        @JsonProperty("alias")
        String alias;
    }

    public static class NetworkTopology_J
    {
        public NetworkTopology_J(){

        }
        @JsonProperty("nodes")
        private ArrayList<NetworkNode_J> data_centers_switches;
        //@JsonProperty("translation")
        //private ArrayList<NodeAliasMap_J> aliases;

        public ArrayList<NetworkNode_J> getData_centers_switches() {
            return data_centers_switches;
        }

        public void setData_centers_switches(ArrayList<NetworkNode_J> data_centers_switches) {
            this.data_centers_switches = data_centers_switches;
        }
    }


}

class NetworkNode
{
    final static Logger logger = Logger.getLogger(NetworkNode.class);

    private List<Node> output_links = new ArrayList<Node>();
    private List<Node> input_links = new ArrayList<Node>();
    private boolean is_switch = false;

    public List<Node> get_output_links() {
        return output_links;
    }

    public void set_output_links(List<Node> output_links) {
        this.output_links = output_links;
    }

    public List<Node> get_input_links() {
        return input_links;
    }

    public void set_onput_links(List<Node> input_links) {
        this.input_links = input_links;
    }

    public boolean is_switch() {
        return is_switch;
    }

    public void set_is_switch(boolean is_switch) {
        this.is_switch = is_switch;
    }



}
