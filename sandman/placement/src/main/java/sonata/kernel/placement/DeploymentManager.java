package sonata.kernel.placement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.jaxen.Function;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.heat.Stack;
import org.openstack4j.openstack.OSFactory;
import sonata.kernel.VimAdaptor.commons.DeployServiceData;
import sonata.kernel.VimAdaptor.commons.heat.HeatResource;
import sonata.kernel.VimAdaptor.commons.heat.HeatTemplate;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;
import sonata.kernel.placement.config.PlacementConfig;
import sonata.kernel.placement.config.PopResource;
import sonata.kernel.placement.monitor.FunctionMonitor;
import sonata.kernel.placement.monitor.MonitorManager;
import sonata.kernel.placement.net.LinkChain;
import sonata.kernel.placement.net.TranslatorChain;
import sonata.kernel.placement.service.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Manages the deployed service
 * One service can be deployed, updated and removed
 */
public class DeploymentManager implements Runnable{

    final static Logger logger = Logger.getLogger(DeploymentManager.class);

    // Maps datacenter name to stack name
    static Map<String, String> dcStackMap = new HashMap<String, String>();
    static List<LinkChain> currentChaining = new ArrayList<LinkChain>();
    static ServiceInstance currentInstance;
    static PlacementMapping currentMapping;
    static DeployServiceData currentDeployData;
    static List<PopResource> currentPops;
    static List<String> currentNodes;

    public void run(){
        while (true) {

            try {
                logger.debug("Waiting for message");
                MessageQueue.MessageQueueData message = MessageQueue.get_deploymentQ().take();
                if(message.message_type == MessageQueue.MessageType.TERMINATE_MESSAGE) {

                    logger.info("Terminate Message");
                    tearDown();
                    return;

                } else if (message.message_type == MessageQueue.MessageType.DEPLOY_MESSAGE) {

                    MessageQueue.MessageQueueDeployData deployMessage = (MessageQueue.MessageQueueDeployData) message;
                    logger.info("Deploy Message - deploy index "+deployMessage.index);
                    deploy(deployMessage);

                } else if (message.message_type == MessageQueue.MessageType.UNDEPLOY_MESSAGE) {

                    logger.info("Undeploy Message");
                    undeploy();
                } else if (message.message_type == MessageQueue.MessageType.MONITOR_MESSAGE) {

                    logger.info("Monitor Message");
                    MessageQueue.MessageQueueMonitorData monitorMessage = (MessageQueue.MessageQueueMonitorData) message;
                    monitor(monitorMessage);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void deploy(MessageQueue.MessageQueueDeployData message){

        message.responseId = 500;
        message.responseMessage = "Something went terribly wrong";

        try {
            PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();
            PlacementPlugin plugin = PlacementPluginLoader.placementPlugin;

            DeployServiceData data = Catalogue.getPackagetoDeploy(message.index);
            String serviceName = data.getNsd().getName();

            ServiceInstance instance = plugin.initialScaling(data);
            PlacementMapping mapping = plugin.initialPlacement(data, instance, config.getResources());
            List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources(), mapping);

            SimpleDateFormat format = new SimpleDateFormat("yymmddHHmmss");
            String timestamp = format.format(new Date());

            List<FunctionMonitor> vnfMonitors = new ArrayList<FunctionMonitor>();

            Map<PopResource,List<String>> popNodeMap = new HashMap<PopResource,List<String>>();

            List<String> nodeList = new ArrayList<String>();
            List<PopResource> popList = new ArrayList<PopResource>();
            popList.addAll(mapping.popMapping.values());
            for (int i = 0; i < popList.size(); i++) {
                PopResource pop = popList.get(i);
                String popName = pop.getPopName();
                String stackName = timestamp + "-" + serviceName;

                HeatTemplate template = templates.get(i);

                String templateStr = templateToJson(template);

                logger.debug("Template for datacenter: "+popName);
                logger.debug(templateStr);

                deployStack(pop, stackName, templateStr);

                dcStackMap.put(popName, stackName);
                List<String> popNodes = new ArrayList<String>();
                Collection<HeatResource> col = (Collection)template.getResources().values();
                for(HeatResource h: col){
                    if(h.getType().equals("OS::Nova::Server")) {
                        vnfMonitors.add(new FunctionMonitor(pop, stackName, h.getResourceName()));
                        nodeList.add(h.getResourceName());
                        popNodes.add(h.getResourceName());
                    }
                }
                popNodeMap.put(pop, popNodes);
            }

            // Chaining
            List<Pair<Pair<String, String>,Pair<String, String>>> create_chains = instance.get_create_chain();
            List<Pair<Pair<String, String>,Pair<String, String>>> delete_chains = instance.get_delete_chain();
            List<LinkChain> create_link_chains = createLinkChainList(create_chains, dcStackMap, popNodeMap);
            List<LinkChain> delete_link_chains = createLinkChainList(delete_chains, dcStackMap, popNodeMap);

            chain(create_link_chains);
            unchain(delete_link_chains);

            // Monitoring
            MonitorManager.addAndStartMonitor(vnfMonitors);

            currentInstance = instance;
            currentMapping = mapping;
            currentDeployData = data;
            currentPops = popList;
            currentNodes = nodeList;

            message.responseId = 201;
            message.responseMessage = "Created";
        } catch(Exception e) {
            logger.error("Deployment failed",e);
        }
        finally{
            synchronized(message) {
                message.notify();
            }
        }
    }

    public static void deployStack(PopResource pop, String stackName, String templateJsonString){
        OSClient.OSClientV2 os = OSFactory.builderV2()
                .endpoint( pop.getEndpoint())
                .credentials(pop.getUserName(),pop.getPassword())
                .tenantName(pop.getTenantName())
                .authenticate();

        Stack stack = os.heat().stacks().create(Builders.stack()
                .name(stackName)
                .template(templateJsonString)
                .timeoutMins(5L).build());
    }

    public static void undeploy(){
        MonitorManager.stopAndRemoveAllMonitors();

        unchain(currentChaining);

        if(currentMapping != null) {
            List<PopResource> popList = new ArrayList<PopResource>();
            popList.addAll(currentMapping.popMapping.values());
            for (PopResource pop : popList) {
                String stackName = dcStackMap.get(pop.getPopName());
                undeployStack(pop, stackName);
            }
        }
        currentInstance = null;
        currentMapping = null;
        currentDeployData = null;
        currentPops = null;
        currentNodes = null;
        dcStackMap.clear();
    }

    public static void monitor(MessageQueue.MessageQueueMonitorData message){
        // TODO: create MonitorMessage and pass the history to the plugin

        if(true)
            return;

        PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();
        PlacementPlugin plugin = PlacementPluginLoader.placementPlugin;
        MonitorMessage monitorMessage = new MonitorMessage(null, null);
        ServiceInstance instance = plugin.updateScaling(currentDeployData, currentInstance, monitorMessage);
        PlacementMapping mapping = plugin.updatePlacement(currentDeployData, instance, config.getResources(), currentMapping);
        String serviceName = currentDeployData.getNsd().getName();

        List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources(), mapping);

        SimpleDateFormat format = new SimpleDateFormat("yymmddHHmmss");
        String timestamp = format.format(new Date());

        List<FunctionMonitor> vnfMonitors = new ArrayList<FunctionMonitor>();
        List<String> nodeList = new ArrayList<String>();

        Map<PopResource,List<String>> popNodeMap = new HashMap<PopResource,List<String>>();

        List<PopResource> popList = new ArrayList<PopResource>();
        popList.addAll(mapping.popMapping.values());

        List<String> removedNodes = new ArrayList<String>();
        List<PopResource> removedPops = new ArrayList<PopResource>();
        for(PopResource oldPop: currentPops) {
            if (!popList.contains(oldPop))
                removedPops.add(oldPop);
        }
        for(PopResource pop: removedPops){
            dcStackMap.remove(pop.getPopName());
        }

        for (int i = 0; i < popList.size(); i++) {
            PopResource pop = popList.get(i);
            String popName = pop.getPopName();

            String stackName = dcStackMap.get(popName); // get old stack name

            if(stackName == null) // it's a pop not used before
                stackName = timestamp + "-" + serviceName;

            HeatTemplate template = templates.get(i);

            String templateStr = templateToJson(template);

            logger.debug("Template for datacenter: "+popName);
            logger.debug(templateStr);

            deployStack(pop, stackName, templateStr);

            List<String> popNodes = new ArrayList<String>();
            dcStackMap.put(popName, stackName);
            Collection<HeatResource> col = (Collection)template.getResources().values();
            for(HeatResource h: col){
                if(h.getType().equals("OS::Nova::Server")) {
                    vnfMonitors.add(new FunctionMonitor(pop, stackName, h.getResourceName()));
                    nodeList.add(h.getResourceName());
                    popNodes.add(h.getResourceName());
                }
            }
            popNodeMap.put(pop, popNodes);
        }
        for(String oldNode: currentNodes)
            if(!nodeList.contains(oldNode))
                removedNodes.add(oldNode);

        // Find out function monitor changes
        List<FunctionMonitor> addedFunctions = new ArrayList<FunctionMonitor>();
        List<FunctionMonitor> removedFunctions = new ArrayList<FunctionMonitor>();

        for(FunctionMonitor monitor:MonitorManager.monitors){
            if(removedNodes.contains(monitor.function))
                removedFunctions.add(monitor);

        }
        for(FunctionMonitor monitor:vnfMonitors){
            if(!vnfMonitors.contains(monitor))
                addedFunctions.add(monitor);
        }

        // Chaining
        List<Pair<Pair<String, String>,Pair<String, String>>> create_chains = instance.get_create_chain();
        List<Pair<Pair<String, String>,Pair<String, String>>> delete_chains = instance.get_delete_chain();
        List<LinkChain> create_link_chains = createLinkChainList(create_chains, dcStackMap, popNodeMap);
        List<LinkChain> delete_link_chains = createLinkChainList(delete_chains, dcStackMap, popNodeMap);

        chain(create_link_chains);
        unchain(delete_link_chains);

        // Monitoring
        MonitorManager.updateMonitors(addedFunctions, removedFunctions);

        currentInstance = instance;
        currentMapping = mapping;
        currentPops = popList;
        currentNodes = nodeList;
    }

    public static List<LinkChain> createLinkChainList(List<Pair<Pair<String, String>,Pair<String, String>>> chains, Map<String, String> stackMap, Map<PopResource,List<String>> popNodeMap){
        List<LinkChain> linkChainList = new ArrayList<LinkChain>();

        for (Pair<Pair<String, String>,Pair<String, String>> c : chains){
            Pair<String,String> left = c.getLeft();
            Pair<String,String> right = c.getRight();
            String leftNodeName = null;
            String rightNodeName = null;

            PopResource leftPop = null;
            PopResource rightPop = null;

            for(Map.Entry<PopResource, List<String>> popHeatEntry: popNodeMap.entrySet()){
                for(String nodeName: popHeatEntry.getValue()){
                    if(nodeName.equals(left.getLeft()))
                        leftPop = popHeatEntry.getKey();
                    if(nodeName.equals(right.getLeft()))
                        rightPop = popHeatEntry.getKey();
                }
            }

            if(leftPop == null || rightPop == null)
                continue;

            String leftStack = stackMap.get(leftPop.getPopName());
            String rightStack = stackMap.get(rightPop.getPopName());

            linkChainList.add(new LinkChain(leftPop, leftStack, left.getLeft(), left.getRight(),
                    rightPop, rightStack, right.getLeft(), right.getRight()));
        }
        return linkChainList;
    }

    public static void chain(List<LinkChain> chains){
        System.out.println("Chain "+chains.size());
        for(LinkChain chain : chains) {
            TranslatorChain.chain(chain);
            currentChaining.add(chain);
        }
    }

    public static void unchain(List<LinkChain> chains){
        for(LinkChain chain :  chains) {
            TranslatorChain.unchain(chain);
            currentChaining.remove(chain);
        }
    }

    public static void tearDown(){
        undeploy();
        MonitorManager.closeConnectionPool();
    }

    public static void undeployStack(PopResource pop, String stackName) {
        OSClient.OSClientV2 os = OSFactory.builderV2()
                .endpoint(pop.getEndpoint())
                .credentials(pop.getUserName(), pop.getPassword())
                .tenantName(pop.getTenantName())
                .authenticate();

        // Get all stacks from datacenter
        List<? extends Stack> stackList = os.heat().stacks().list();

        // Find correct stack
        for (Stack stack : stackList) {
            if (stack.getName().equals(stackName))
                os.heat().stacks().delete(stack.getName(), stack.getId());
        }
    }

    // Utility

    static ObjectMapper mapper = new ObjectMapper(new JsonFactory());
    static SimpleModule module = new SimpleModule();

    // Initialize JSON mapper
    static {

        mapper.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        mapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        module.addDeserializer(Unit.class, new UnitDeserializer());
        mapper.registerModule(module);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);

    }

    public static String templateToJson(HeatTemplate template) {
        try {
            return mapper.writeValueAsString(template);
        } catch (JsonProcessingException e) {
            return null;
        }
    }


}
