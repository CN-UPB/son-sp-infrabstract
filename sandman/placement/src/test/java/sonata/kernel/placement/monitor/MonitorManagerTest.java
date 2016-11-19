package sonata.kernel.placement.monitor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;
import sonata.kernel.VimAdaptor.commons.DeployServiceData;
import sonata.kernel.VimAdaptor.commons.heat.HeatTemplate;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;
import sonata.kernel.placement.DeploymentManager;
import sonata.kernel.placement.PackageLoader;
import sonata.kernel.placement.PlacementConfigLoader;
import sonata.kernel.placement.config.PlacementConfig;
import sonata.kernel.placement.config.PopResource;
import sonata.kernel.placement.service.*;

import java.nio.file.Paths;
import java.util.List;

public class MonitorManagerTest {
    final static Logger logger = Logger.getLogger(MonitorManagerTest.class);

    @Test @Ignore
    public void singleVnfTest(){
        PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();

        PopResource pop = config.getResources().get(0);

        String stackName = "mon";
        String nodeName = "single1:9df6a98f-9e11-4cb7-b3c0-InAdUnitTest";

        DeployServiceData data = PackageLoader.loadPackageFromDisk(Paths.get("YAML","packages","singlevnf", "singlevnf.son").toString());

        PlacementPlugin plugin = new DefaultPlacementPlugin();

        ServiceInstance instance = plugin.initialScaling(data);
        PlacementMapping mapping = plugin.initialPlacement(data, instance, config.getResources());
        List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources(), mapping);

        String template = null;
        try {
            template = templateToJson(templates.get(0));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        System.out.println(template);

        try {
            DeploymentManager.deployStack(pop, stackName, template);
        } catch(Exception e) {
            e.printStackTrace();
            // Ignore deploy failure for now
        }

        logger.info(System.currentTimeMillis()+" \tStart Monitoring");

        MonitorManager.intervalMillis = 2000;

        MonitorManager.addAndStartMonitor(new FunctionMonitor(pop, stackName, nodeName));

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        logger.info(System.currentTimeMillis()+" \tStop Monitoring");
        MonitorManager.stopAndRemoveAllMonitors();
        logger.info(System.currentTimeMillis()+" \tClosing pool");
        MonitorManager.closeConnectionPool();
        logger.info(System.currentTimeMillis()+" \tDeleting stack");
        try {
            DeploymentManager.undeployStack(pop, stackName);
        } catch(Exception e){
            e.printStackTrace();
            // Ignore undeploy failure for now
        }

    }

    public static String templateToJson(HeatTemplate template) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        SimpleModule module = new SimpleModule();
        mapper.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        mapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        module.addDeserializer(Unit.class, new UnitDeserializer());
        mapper.registerModule(module);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        return mapper.writeValueAsString(template);
    }
}