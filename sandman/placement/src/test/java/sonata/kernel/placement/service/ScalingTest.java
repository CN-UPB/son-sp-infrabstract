package sonata.kernel.placement.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.Test;
import sonata.kernel.VimAdaptor.commons.DeployServiceData;
import sonata.kernel.VimAdaptor.commons.heat.HeatTemplate;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;
import sonata.kernel.placement.HeatStackCreate;
import sonata.kernel.placement.PackageLoader;
import sonata.kernel.placement.PlacementConfigLoader;
import sonata.kernel.placement.config.PerformanceThreshold;
import sonata.kernel.placement.config.PlacementConfig;
import sonata.kernel.placement.monitor.MonitorStats;
import org.apache.log4j.Logger;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScalingTest {

    final static Logger logger = Logger.getLogger(ScalingTest.class);
    @Test
    public void UpdateThresholdTest()
    {
        PerformanceThreshold threshold = new PerformanceThreshold();
        threshold.setCpu_lower_l(40);
        threshold.setCpu_upper_l(60);
        threshold.setMem_lower_l(10);
        threshold.setMem_upper_l(70);
        threshold.setScale_in_lower_l(70);
        threshold.setScale_out_upper_l(60);
        threshold.setVnfId("vnf_world_domination");
        threshold.setHistory_check(2);

        ServiceInstance instance = new ServiceInstance();
        Map<String, List<MonitorStats>> stats_history = new HashMap<String, List<MonitorStats>>();
        ComputeMetrics metrics = new ComputeMetrics(instance, stats_history);

        logger.info("Adding new performance threshold: " + threshold);
        metrics.update_threshold("vnf_world_domination", threshold);
    }

    @Test
    public void UpdateThresholdTest2()
    {

        PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();

        PerformanceThreshold threshold = config.getThreshold().get(0);
        logger.info("Threshold level for " + threshold.getVnfId() + " : " + threshold);

        PerformanceThreshold threshold_new = new PerformanceThreshold();
        threshold_new.setCpu_lower_l(40);
        threshold_new.setCpu_upper_l(60);
        threshold_new.setMem_lower_l(10);
        threshold_new.setMem_upper_l(70);
        threshold_new.setScale_in_lower_l(70);
        threshold_new.setScale_out_upper_l(60);
        threshold_new.setVnfId(threshold.getVnfId());
        threshold_new.setHistory_check(2);

        ServiceInstance instance = new ServiceInstance();
        Map<String, List<MonitorStats>> stats_history = new HashMap<String, List<MonitorStats>>();
        ComputeMetrics metrics = new ComputeMetrics(instance, stats_history);

        logger.info("Updating new performance threshold: " + threshold_new);
        metrics.update_threshold("vnf_world_domination", threshold_new);
    }

    @Test
    public void GenerateServiceGraphTest()
    {
        PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();

        DeployServiceData data = PackageLoader.loadPackageFromDisk(Paths.get("YAML", "test.son").toString());

        PlacementPlugin plugin = new DefaultPlacementPlugin();

        ServiceInstance instance = plugin.initialScaling(data);

        logger.info("Service Graph pre-scale out.");
        ServiceGraph graph1 = new ServiceGraph(instance);
        Node node1 = graph1.generate_graph();
        traverseGraph(node1);

        logger.info("Forwarding Graph: ns:fg01 Forwarding Path ns:fg01:fp01");
        Node forwarding_path_1 = graph1.get_forwarding_path("ns:fg01",  "ns:fg01:fp01");
        if(null!=forwarding_path_1)
            traverseGraph(forwarding_path_1);
        else
            logger.error("Forwarding Graph: ns:fg01 Forwarding Path ns:fg01:fp01 not found");

        HashMap<String, List<MonitorStats>> stats_history = new HashMap<String, List<MonitorStats>>();
        MonitorMessage trigger = new MonitorMessage(MonitorMessage.SCALE_TYPE.SCALE_OUT, stats_history);

        instance = plugin.updateScaling(data, instance, trigger);

        logger.info("Service Graph post-scale out.");
        ServiceGraph graph2 = new ServiceGraph(instance);
        Node node2 = graph2.generate_graph();
        traverseGraph(node2);

        logger.info("Forwarding Graph: ns:fg01 Forwarding Path ns:fg01:fp02");
        Node forwarding_path_2 = graph2.get_forwarding_path("ns:fg01",  "ns:fg01:fp02");
        if(null!=forwarding_path_2)
        {
            logger.error("Forwarding Graph: ns:fg01 Forwarding Path ns:fg01:fp02 should NOT exist");
        }

        return;

    }

    private void traverseGraph(Node root)
    {
        StringBuilder str = new StringBuilder();
        str.append(root.get_instance_name());
        if(root.get_output_links().size()==0)
            return;
        str.append("->[");
        for(Node nn : root.get_output_links())
        {
            str.append(" " + nn.get_instance_name());
        }
        str.append(" ]");
        logger.info(str.toString());

        for(Node nn : root.get_output_links())
        {
            traverseGraph(nn);
        }

        return;
    }


    //@Test
    public void ScaleOut_1() {
        System.out.println(new File("").getAbsolutePath());

        PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();

        DeployServiceData data = PackageLoader.loadPackageFromDisk(Paths.get("YAML", "test.son").toString());

        PlacementPlugin plugin = new DefaultPlacementPlugin();

        ServiceInstance instance = plugin.initialScaling(data);

        PlacementMapping mapping = plugin.initialPlacement(data, instance, config.getResources());


        //Perform sclae out.
        /*
        {"CPU_cores": 4,
         "SYS_time": 1480936009023641088,
         "PIDS": 2,
         "NET_in/s": 394,
         "BLOCK_read": 0,
         "MEM_limit": 16827117568,
         "NET_out/s": 383,
         "BLOCK_write": 0,
         "CPU_%": 0.0032249413036155128,
         "MEM_used": 618496,
         "MEM_%": 3.675590887747698e-05}
        */

        //Overload detection for firewall1 - CPU load greater than 70%
        MonitorStats stat1 = new MonitorStats();
        stat1.setCpuCores(4);
        stat1.setCpu(3.5); // > 2.8 (70%) --> CPU Overloaded
        stat1.setMemoryLimit(16827117568L);
        stat1.setMemoryPercentage(0.10000001445286152053109610745188f); //> 10%  and  < 70% (Normal memory consumption)

        //Normal load for tcpdump
        MonitorStats stat2 = new MonitorStats();
        stat2.setCpuCores(4);
        stat2.setCpu(1.5); // > 0.4 (10%) and < 2.8 (70%) --> Normal CPU load
        stat2.setMemoryLimit(16827117568L);
        stat2.setMemoryPercentage(0.10000001445286152053109610745188f); //> 10%  and  < 70% (Normal memory consumption)

        HashMap<String, MonitorStats> stats = new HashMap<String, MonitorStats>();
        HashMap<String, List<MonitorStats>> stats_history = new HashMap<String, List<MonitorStats>>();

        stats.put("tcpdump1", stat1);
        stats.put("firwall1", stat2);

        MonitorMessage trigger = new MonitorMessage(MonitorMessage.SCALE_TYPE.MONITOR_STATS, stats_history);

        instance = plugin.updateScaling(data, instance, trigger);

        mapping = plugin.initialPlacement(data, instance, config.getResources());

        List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources(), mapping);

        assert templates.size() == 1;

        for (HeatTemplate template : templates) {
            HeatStackCreate createStack = new HeatStackCreate();
            createStack.stackName = "MyLittleStack";
            createStack.template = template;
            ObjectMapper mapper2 = new ObjectMapper(new JsonFactory());
            mapper2.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
            mapper2.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
            mapper2.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
            mapper2.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            mapper2.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            SimpleModule module = new SimpleModule();
            module.addDeserializer(Unit.class, new UnitDeserializer());
            mapper2.registerModule(module);
            mapper2.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
            try {
                String body = mapper2.writeValueAsString(template);
                System.out.println(body);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }

    //@Test
    public void ScaleIn_1() {
        System.out.println(new File("").getAbsolutePath());

        PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();

        DeployServiceData data = PackageLoader.loadPackageFromDisk(Paths.get("YAML", "test.son").toString());

        PlacementPlugin plugin = new DefaultPlacementPlugin();

        ServiceInstance instance = plugin.initialScaling(data);

        PlacementMapping mapping = plugin.initialPlacement(data, instance, config.getResources());


        //Perform sclae out.
        /*
        {"CPU_cores": 4,
         "SYS_time": 1480936009023641088,
         "PIDS": 2,
         "NET_in/s": 394,
         "BLOCK_read": 0,
         "MEM_limit": 16827117568,
         "NET_out/s": 383,
         "BLOCK_write": 0,
         "CPU_%": 0.0032249413036155128,
         "MEM_used": 618496,
         "MEM_%": 3.675590887747698e-05}
        */

        //Overload detection for firewall1 - CPU load greater than 70%
        MonitorStats stat1 = new MonitorStats();
        stat1.setCpuCores(4);
        stat1.setCpu(3.5); // > 2.8 (70%) --> CPU Overloaded
        stat1.setMemoryLimit(16827117568L);
        stat1.setMemoryPercentage(0.10000001445286152053109610745188f); //> 10%  and  < 70% (Normal memory consumption)

        //Normal load for tcpdump
        MonitorStats stat2 = new MonitorStats();
        stat2.setCpuCores(4);
        stat2.setCpu(1.5); // > 0.4 (10%) and < 2.8 (70%) --> Normal CPU load
        stat2.setMemoryLimit(16827117568L);
        stat2.setMemoryPercentage(0.10000001445286152053109610745188f); //> 10%  and  < 70% (Normal memory consumption)

        HashMap<String, MonitorStats> stats = new HashMap<String, MonitorStats>();
        HashMap<String, List<MonitorStats>> stats_history = new HashMap<String, List<MonitorStats>>();

        stats.put("firewall1", stat1);
        stats.put("tcpdump1", stat2);

        MonitorMessage trigger = new MonitorMessage(MonitorMessage.SCALE_TYPE.MONITOR_STATS, stats_history);

        instance = plugin.updateScaling(data, instance, trigger);
        mapping = plugin.initialPlacement(data, instance, config.getResources());

        List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources(), mapping);

        assert templates.size() == 1;

        for (HeatTemplate template : templates) {
            HeatStackCreate createStack = new HeatStackCreate();
            createStack.stackName = "MyLittleStack";
            createStack.template = template;
            ObjectMapper mapper2 = new ObjectMapper(new JsonFactory());
            mapper2.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
            mapper2.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
            mapper2.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
            mapper2.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            mapper2.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            SimpleModule module = new SimpleModule();
            module.addDeserializer(Unit.class, new UnitDeserializer());
            mapper2.registerModule(module);
            mapper2.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
            try {
                String body = mapper2.writeValueAsString(template);
                System.out.println(body);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }


}
