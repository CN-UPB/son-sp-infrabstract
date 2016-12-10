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
import sonata.kernel.placement.net.*;
import sonata.kernel.placement.monitor.MonitorStats;
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
    static Map<LinkPort,LinkLoadbalance> currentLoadbalanceMap = new HashMap<LinkPort,LinkLoadbalance>();
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

                    MessageQueue.MessageQueueUnDeployData undeployMessage = (MessageQueue.MessageQueueUnDeployData) message;
                    logger.info("Undeploy Message");
                    undeploy(undeployMessage);
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

        if(currentInstance != null) {
            logger.info("Already deployed, undeploy first");
            message.responseId = 406;
            message.responseMessage = "Already deployed, undeploy first";
            synchronized(message) {
                message.notify();
            }
            return;
        }

        try {
            PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();
            PlacementPlugin plugin = PlacementPluginLoader.placementPlugin;

            DeployServiceData data = Catalogue.getPackagetoDeploy(message.index);
            String serviceName = data.getNsd().getName();
            logger.info("Deloying service: "+serviceName);

            ServiceInstance instance = plugin.initialScaling(data);
            PlacementMapping mapping = plugin.initialPlacement(data, instance, config.getResources());
            List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources(), mapping);

            Map<String, FunctionInstance> functionMap = new HashMap<String, FunctionInstance>();
            for(Map<String, FunctionInstance> map: instance.function_list.values()){
                for(Map.Entry<String, FunctionInstance> entry : map.entrySet())
                    functionMap.put(entry.getValue().name,entry.getValue());
            }

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

                logger.debug("Template for datacenter: "+popName+" Stack name: "+stackName);
                logger.debug(templateStr);

                deployStack(pop, stackName, templateStr);

                dcStackMap.put(popName, stackName);
                List<String> popNodes = new ArrayList<String>();
                Collection<HeatResource> col = (Collection)template.getResources().values();
                for(HeatResource h: col){
                    if(h.getType().equals("OS::Nova::Server")) {
                        String functionInstanceName = h.getResourceName();
                        functionInstanceName = functionInstanceName.substring(0,functionInstanceName.lastIndexOf(":"));
                        FunctionMonitor monitor = new FunctionMonitor(pop, stackName, h.getResourceName());
                        monitor.instance = functionMap.get(functionInstanceName);
                        vnfMonitors.add(monitor);
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

            // Loadbalancing
            unloadbalance(currentLoadbalanceMap.values());
            createLinkLoadbalanceMap(currentLoadbalanceMap, create_link_chains, delete_link_chains);
            loadbalance(currentLoadbalanceMap.values());

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

    public static void undeploy(MessageQueue.MessageQueueUnDeployData message){

        try {
            try {
                MonitorManager.stopAndRemoveAllMonitors();
            } catch (Exception e) {
                logger.error(e);
                logger.trace(e);
            }

            try {
                unchain(currentChaining);
            } catch (Exception e) {
                logger.error(e);
                logger.trace(e);
            }

            // Loadbalancing
            try {
                unloadbalance(currentLoadbalanceMap.values());
            } catch (Exception e) {
                logger.error(e);
                logger.trace(e);
            }

            if (currentMapping != null) {
                List<PopResource> popList = new ArrayList<PopResource>();
                popList.addAll(currentMapping.popMapping.values());
                for (PopResource pop : popList) {
                    String stackName = dcStackMap.get(pop.getPopName());
                    try {
                        undeployStack(pop, stackName);
                    } catch (Exception e) {
                        logger.error(e);
                    }
                }
            }
            currentInstance = null;
            currentMapping = null;
            currentDeployData = null;
            currentPops = null;
            currentNodes = null;
            currentLoadbalanceMap.clear();
            dcStackMap.clear();

            if(message != null) {
                message.responseId = 200;
                message.responseMessage = "OK";
            }
        } finally {
            if (message != null) {
                synchronized (message) {
                    message.notify();
                }
            }
        }
    }

    public static void monitor(MessageQueue.MessageQueueMonitorData message){

        // TODO: For ... reasons don't do anything at this moment
        if(true)
            return;

        PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();
        PlacementPlugin plugin = PlacementPluginLoader.placementPlugin;

        MonitorMessage monitorMessage = new MonitorMessage(MonitorMessage.SCALE_TYPE.MONITOR_STATS, message.statsMap, message.statsHistoryMap);
        ServiceInstance instance = plugin.updateScaling(currentDeployData, currentInstance, monitorMessage);

        if (monitorMessage.type == MonitorMessage.SCALE_TYPE.NO_SCALE) {
            return;
        }

        if(monitorMessage.type == MonitorMessage.SCALE_TYPE.SCALE_OUT)
            logger.info("Scale out");
        else if(monitorMessage.type == MonitorMessage.SCALE_TYPE.SCALE_IN)
            logger.info("Scale in");

        PlacementMapping mapping = plugin.updatePlacement(currentDeployData, instance, config.getResources(), currentMapping);
        String serviceName = currentDeployData.getNsd().getName();

        List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources(), mapping);

        Map<String, FunctionInstance> functionMap = new HashMap<String, FunctionInstance>();
        for(Map<String, FunctionInstance> map: instance.function_list.values()){
            for(Map.Entry<String, FunctionInstance> entry : map.entrySet())
                functionMap.put(entry.getValue().name,entry.getValue());
        }

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

            logger.debug("Template for datacenter: "+popName+" Stack name: "+stackName);
            logger.debug(templateStr);

            deployStack(pop, stackName, templateStr);

            List<String> popNodes = new ArrayList<String>();
            dcStackMap.put(popName, stackName);
            Collection<HeatResource> col = (Collection)template.getResources().values();
            for(HeatResource h: col){
                if(h.getType().equals("OS::Nova::Server")) {
                    String functionInstanceName = h.getResourceName();
                    functionInstanceName = functionInstanceName.substring(0,functionInstanceName.lastIndexOf(":"));
                    FunctionMonitor monitor = new FunctionMonitor(pop, stackName, h.getResourceName());
                    monitor.instance = functionMap.get(functionInstanceName);
                    vnfMonitors.add(monitor);
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

        // Loadbalancing
        unloadbalance(currentLoadbalanceMap.values());
        createLinkLoadbalanceMap(currentLoadbalanceMap, create_link_chains, delete_link_chains);
        loadbalance(currentLoadbalanceMap.values());

        // Monitoring
        MonitorManager.updateMonitors(addedFunctions, removedFunctions);

        currentInstance = instance;
        currentMapping = mapping;
        currentPops = popList;
        currentNodes = nodeList;
    }

    public static void createLinkLoadbalanceMap(Map<LinkPort, LinkLoadbalance> lbMap,List<LinkChain> createChains, List<LinkChain> deleteChains){
        // Remove loadbalancing rules, that are to be deleted
        for(LinkChain chain: deleteChains){
            if(lbMap.containsKey(chain.srcPort)){
                LinkLoadbalance lb = lbMap.get(chain.srcPort);
                lb.dstPorts.remove(chain.dstPort);
                // Keep empty loadbalance objects in case they will be used in creation loop later
            } else {
                // Nothing to do...
            }
        }
        // Add new
        for (LinkChain chain: createChains){
            if(!lbMap.containsKey(chain.srcPort)){
                List<LinkPort> dstPorts = new ArrayList<LinkPort>();
                dstPorts.add(chain.dstPort);
                LinkLoadbalance lb = new LinkLoadbalance(chain.srcPort, dstPorts);
                lbMap.put(chain.srcPort, lb);
            } else {
                LinkLoadbalance lb = lbMap.get(chain.srcPort);
                if(!lb.dstPorts.contains(chain.dstPort))
                    lb.dstPorts.add(chain.dstPort);
            }
        }
        // Remove empty loadbalancing rules, or rules concerning only one destination port
        List<LinkLoadbalance> lbList = new ArrayList<LinkLoadbalance>();
        lbList.addAll(lbMap.values());
        for(LinkLoadbalance lb : lbList){
            if(lb.dstPorts.size()<=1)
                lbMap.remove(lb.srcPort);
        }
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
        for(LinkChain chain : chains) {
            TranslatorChain.chain(chain);
            currentChaining.add(chain);
        }
    }

    public static void unchain(List<LinkChain> chains){
        for(LinkChain chain :  chains) {
            TranslatorChain.unchain(chain);
            if(chains != currentChaining)
                currentChaining.remove(chain);
        }
        if (chains == currentChaining)
            currentChaining.clear();
    }

    public static void loadbalance(Collection<LinkLoadbalance> balances){
        for(LinkLoadbalance balance : balances) {
            // No loadbalancing for only
            if(balance.dstPorts.size()>1)
                TranslatorLoadbalancer.loadbalance(balance);
        }
    }

    public static void unloadbalance(Collection<LinkLoadbalance> balances){
        for(LinkLoadbalance balance : balances)
            TranslatorLoadbalancer.unloadbalance(balance.srcPort);
    }

    public static void tearDown(){
        undeploy(null);
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
