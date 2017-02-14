package sonata.kernel.placement.net;

import org.openstack4j.api.OSClient.OSClientV2;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.Subnet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;

import sonata.kernel.VimAdaptor.commons.DeployServiceData;
import sonata.kernel.VimAdaptor.commons.heat.HeatTemplate;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;
import sonata.kernel.placement.DatacenterManager;
import sonata.kernel.placement.pd.PackageLoader;
import sonata.kernel.placement.config.PlacementConfigLoader;
import sonata.kernel.placement.config.PlacementConfig;
import sonata.kernel.placement.config.PopResource;
import sonata.kernel.placement.service.DefaultPlacementPlugin;
import sonata.kernel.placement.service.PlacementPlugin;
import sonata.kernel.placement.service.ServiceHeatTranslator;
import sonata.kernel.placement.service.ServiceInstance;

import java.nio.file.Paths;
import java.util.List;


/**
 * Created by Manoj.
 */
public class TranslatorNetworkTest {
	
	
	
	//@Test
	public void create_network() throws Exception {

		PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();
        DatacenterManager.initialize();
		List<PopResource> resources = config.getResources();
		PopResource pop1 = config.getResources().get(0);
		String stackName = "TestStack";
		DeployServiceData data = PackageLoader.loadPackageFromDisk(Paths.get("testScripts","packages","sonata-demo","sonata-demo.son").toString());

        PlacementPlugin plugin = new DefaultPlacementPlugin();

        ServiceInstance instance = plugin.initialScaling(data);
        List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources());

        String template = null;
        try {
            template = templateToJson(templates.get(0));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        
        try {
            TranslatorHeat.deployStack(pop1, stackName, template);
        } catch(Exception e) {
            e.printStackTrace();
            // Ignore deploy failure for now
        }

		for(PopResource pop : resources) {
            OSClientV2 os = TranslatorNetwork.authenticate_instance(pop);
            System.out.println(os.getEndpoint());

            Network network = TranslatorNetwork.create_network(os, "Test6", "fc394f2ab2df4114bde39905f800dc57");
            String netId = network.getId();
            //Thread.sleep(10000);
            Subnet subnet = TranslatorNetwork.create_subnet(os, "Test2", netId, "fc394f2ab2df4114bde39905f800dc57", "10.0.1.0", "10.0.1.7", "10.0.1.0/29");
            String subId = subnet.getId();
            //Thread.sleep(10000);
            Port port = TranslatorPort.create_port(os, "PortTest", netId, "192.0.1.1", subId);
            String portId = port.getId();
            //Thread.sleep(10000);
            TranslatorPort.delete_port(os, portId);
            //Thread.sleep(10000);
            TranslatorNetwork.delete_subnet(os, subId);
            //Thread.sleep(10000);
            TranslatorNetwork.delete_network(os, netId);
		}
		try {
            TranslatorHeat.undeployStack(pop1, stackName);
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
	
