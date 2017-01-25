package sonata.kernel.placement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.bcel.generic.POP;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.jaxen.Function;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.heat.Stack;
import org.openstack4j.model.heat.StackUpdate;
import org.openstack4j.model.heat.builder.StackUpdateBuilder;
import org.openstack4j.openstack.OSFactory;
import org.openstack4j.openstack.heat.domain.HeatStackUpdate;
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
    static Map<PopResource, String> dcStackMap = new HashMap<PopResource, String>();
    static List<LinkChain> currentChaining = new ArrayList<LinkChain>();
    static Map<LinkPort,LinkLoadbalance> currentLoadbalanceMap = new HashMap<LinkPort,LinkLoadbalance>();
    static ServiceInstance currentInstance;
    static DeployServiceData currentDeployData;
    static List<PopResource> currentPops;
    static List<String> currentNodes;


    static MonitorMessage.SCALE_TYPE nextScale = null;

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
            } catch (Exception e) {
                e.printStackTrace();
                logger.error(e);
                logger.trace(e);
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
            List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources());

            // Maps function unique id -> function_instance
            Map<String, FunctionInstance> functionMap = new HashMap<String, FunctionInstance>();
            // Go through map: function name -> <function unique id -> function_instance>
            for(Map<String, FunctionInstance> map: instance.function_list.values()){
                for(Map.Entry<String, FunctionInstance> entry : map.entrySet())
                    functionMap.put(entry.getValue().name,entry.getValue());
            }

            SimpleDateFormat format = new SimpleDateFormat("yymmddHHmmss");
            String timestamp = format.format(new Date());

            List<FunctionMonitor> vnfMonitors = new ArrayList<FunctionMonitor>();

            Map<String, PopResource> popNodeMap = new HashMap<String, PopResource>();

            List<String> usedNodes = new ArrayList<String>();
            List<PopResource> allPops = config.getResources();
            List<PopResource> usedPops = new ArrayList<PopResource>();

            for (int i = 0; i < allPops.size(); i++) {
                PopResource pop = allPops.get(i);
                String stackName = timestamp + "-" + serviceName;
                HeatTemplate template = templates.get(i);

                if(template == null)
                    continue;
                usedPops.add(pop);

                dcStackMap.put(pop, stackName);

                for(String nodeName: getServerListFromHeatTemplate(template)) {
                    // Populate maps and lists
                    popNodeMap.put(nodeName, pop);
                    usedNodes.add(nodeName);
                    // Prepare function monitors
                    FunctionMonitor monitor = new FunctionMonitor(pop, stackName, nodeName);
                    monitor.instance = functionMap.get(nodeName);
                    vnfMonitors.add(monitor);
                }
            }

            // Execute deployment

            // Deploy stacks
            for (int i = 0; i < allPops.size(); i++) {
                PopResource pop = allPops.get(i);
                if(usedPops.contains(pop)) {
                    String stackName = dcStackMap.get(pop);
                    HeatTemplate template = templates.get(i);
                    String templateStr = templateToJson(template);

                    deployStack(pop, stackName, templateStr);

                    logger.debug("Create stack for datacenter: "+pop.getPopName()+" Stack name: "+stackName);
                    logger.debug(templateStr);
                }
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
            currentDeployData = data;
            currentPops = usedPops;
            currentNodes = usedNodes;

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
        try {
            OSClient.OSClientV2 os = OSFactory.builderV2()
                    .endpoint(pop.getEndpoint())
                    .credentials(pop.getUserName(), pop.getPassword())
                    .tenantName(pop.getTenantName())
                    .authenticate();

            Stack stack = os.heat().stacks().create(Builders.stack()
                    .name(stackName)
                    .template(templateJsonString)
                    .timeoutMins(5L).build());

        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e);
            logger.trace(e);
        }
    }

    public static void updateStack(PopResource pop, String stackName, String templateJsonString){
        try {
            OSClient.OSClientV2 os = OSFactory.builderV2()
                    .endpoint(pop.getEndpoint())
                    .credentials(pop.getUserName(), pop.getPassword())
                    .tenantName(pop.getTenantName())
                    .authenticate();

            // First get stack id
            Stack stack = os.heat().stacks().getStackByName(stackName);
            // Send updated template
            ActionResponse x = os.heat().stacks().update(stackName, stack.getId(),
                    Builders.stackUpdate().template(templateJsonString).timeoutMins(5L).build());

        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e);
            logger.trace(e);
        }
    }

    public static void undeploy(MessageQueue.MessageQueueUnDeployData message){

        try {
            if (currentInstance != null) {
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

                // Undeploy stacks
                for(PopResource pop : currentPops) {
                    String stackName = dcStackMap.get(pop);
                    try {
                        undeployStack(pop, stackName);
                    } catch (Exception e) {
                        logger.error(e);
                    }
                }
            }
            currentInstance = null;
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

        if(currentInstance == null)
            return;

        PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();
        PlacementPlugin plugin = PlacementPluginLoader.placementPlugin;

        MonitorMessage monitorMessage;

        if (message.fakeScaleType != null) {
            nextScale = message.fakeScaleType;
            return;
        }

        if (nextScale == null)
            monitorMessage = new MonitorMessage(MonitorMessage.SCALE_TYPE.MONITOR_STATS, message.statsHistoryMap);
        else {
            monitorMessage = new MonitorMessage(nextScale, message.statsHistoryMap);
            nextScale = null;
        }

        ServiceInstance instance = plugin.updateScaling(currentDeployData, currentInstance, monitorMessage);

        if (instance == null || monitorMessage.type == MonitorMessage.SCALE_TYPE.MONITOR_STATS || monitorMessage.type == MonitorMessage.SCALE_TYPE.NO_SCALE) {
            return;
        }

        if(monitorMessage.type == MonitorMessage.SCALE_TYPE.SCALE_OUT)
            logger.info("Scale out");
        else if(monitorMessage.type == MonitorMessage.SCALE_TYPE.SCALE_IN)
            logger.info("Scale in");

        String serviceName = currentDeployData.getNsd().getName();

        List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources());

        Map<String, FunctionInstance> functionMap = new HashMap<String, FunctionInstance>();
        for(Map<String, FunctionInstance> map: instance.function_list.values()){
            for(Map.Entry<String, FunctionInstance> entry : map.entrySet())
                functionMap.put(entry.getValue().name,entry.getValue());
        }

        SimpleDateFormat format = new SimpleDateFormat("yymmddHHmmss");
        String timestamp = format.format(new Date());

        List<FunctionMonitor> vnfMonitors = new ArrayList<FunctionMonitor>();
        List<String> usedNodes = new ArrayList<String>();

        // Maps node name to pop
        Map<String, PopResource> popNodeMap = new HashMap<String, PopResource>();

        List<PopResource> allPops = config.getResources();
        List<PopResource> usedPops = new ArrayList<PopResource>();      // pops used in the new state
        List<PopResource> oldPops = new ArrayList<PopResource>();       // pops used in old state and new state
        List<PopResource> removedPops = new ArrayList<PopResource>();   // pops used in the old state but not in the new state
        List<PopResource> newPops = new ArrayList<PopResource>();       // pops used in the new state but not in the old state
        Map<PopResource, String> removedStacks = new HashMap<PopResource, String>();

        // Find out used/ old pops
        for(int i=0; i<templates.size(); i++) {
            if (templates.get(i) != null) {
                PopResource pop = allPops.get(i);
                usedPops.add(pop);
                if (currentPops.contains(pop))
                    oldPops.add(pop);
                else
                    newPops.add(pop);
            }
        }

        // Find out removed pops/ stacks and clean up dcStackMap
        for(PopResource oldPop: currentPops) {
            if (!usedPops.contains(oldPop)) {
                removedPops.add(oldPop);
                removedStacks.put(oldPop, dcStackMap.get(oldPop));
                dcStackMap.remove(oldPop);
            }
        }

        for (int i = 0; i < allPops.size(); i++) {
            PopResource pop = allPops.get(i);
            String popName = pop.getPopName();
            logger.debug("Check pop "+popName);

            String stackName = dcStackMap.get(pop); // get old stack name

            if(stackName == null) // it's a pop not used before
                stackName = timestamp + "-" + serviceName;

            HeatTemplate template = templates.get(i);
            if(template == null)
                continue;

            dcStackMap.put(pop, stackName);
            for(String nodeName: getServerListFromHeatTemplate(template)){
                assert !popNodeMap.containsKey(nodeName) : "Vnf name "+nodeName+" is not unique! This should have never happened!";
                popNodeMap.put(nodeName, pop);
                usedNodes.add(nodeName);
            }
        }
        List<String> removedNodes = new ArrayList<String>();
        List<String> newNodes = new ArrayList<String>();
        for(String oldNode: currentNodes) {
            if (!usedNodes.contains(oldNode)) {
                removedNodes.add(oldNode);
            }
        }
        for(String nodeName: usedNodes) {
            if(!currentNodes.contains(nodeName)) {
                newNodes.add(nodeName);
            }
        }

        // Find out function monitor changes
        List<FunctionMonitor> addedFunctions = new ArrayList<FunctionMonitor>();
        List<FunctionMonitor> removedFunctions = new ArrayList<FunctionMonitor>();

        for(FunctionMonitor monitor:MonitorManager.monitors){
            if(removedNodes.contains(monitor.function)) {
                removedFunctions.add(monitor);
                logger.debug("Removed monitor "+monitor.function);
            }
        }
        for(String nodeName: newNodes) {
            PopResource pop = popNodeMap.get(nodeName);
            String stackName = dcStackMap.get(pop);
            FunctionMonitor monitor = new FunctionMonitor(pop, stackName, nodeName);
            monitor.instance = functionMap.get(nodeName);
            addedFunctions.add(monitor);
            logger.debug("New monitor "+monitor.function);
        }

        // Chaining
        List<Pair<Pair<String, String>,Pair<String, String>>> create_chains = instance.get_create_chain();
        List<Pair<Pair<String, String>,Pair<String, String>>> delete_chains = instance.get_delete_chain();
        List<LinkChain> create_link_chains = createLinkChainList(create_chains, dcStackMap, popNodeMap);
        List<LinkChain> delete_link_chains = createLinkChainList(delete_chains, dcStackMap, popNodeMap);

        // Execute changes

        // Remove old resources

        unloadbalance(currentLoadbalanceMap.values());
        unchain(delete_link_chains);

        // Undeploy removed stacks
        for(Map.Entry<PopResource, String> removedStack : removedStacks.entrySet()) {
            PopResource pop = removedStack.getKey();
            String stackName = removedStack.getValue();
            try {
                undeployStack(pop, stackName);
            } catch (Exception e) {
                logger.error(e);
            }
        }

        // Update stacks

        for(int i=0; i<allPops.size(); i++) {
            PopResource pop = allPops.get(i);
            if(oldPops.contains(pop)) {
                String stackName = dcStackMap.get(pop);
                HeatTemplate template = templates.get(i);
                String templateStr = templateToJson(template);
                updateStack(pop, stackName, templateStr);
                logger.debug("Update stack for datacenter: "+pop.getPopName()+" Stack name: "+stackName);
                logger.debug(templateStr);
            }
        }

        // Add new resources

        // Deploy new stacks

        for(int i=0; i<allPops.size(); i++) {
            PopResource pop = allPops.get(i);
            if(newPops.contains(pop)) {
                String stackName = dcStackMap.get(pop);
                HeatTemplate template = templates.get(i);
                String templateStr = templateToJson(template);
                deployStack(pop, stackName, templateStr);
                logger.debug("Create stack for datacenter: "+pop.getPopName()+" Stack name: "+stackName);
                logger.debug(templateStr);
            }
        }

        chain(create_link_chains);
        createLinkLoadbalanceMap(currentLoadbalanceMap, create_link_chains, delete_link_chains);
        loadbalance(currentLoadbalanceMap.values());

        // Monitoring
        MonitorManager.updateMonitors(addedFunctions, removedFunctions);

        currentInstance = instance;
        currentPops = usedPops;
        currentNodes = usedNodes;
        currentPops = usedPops;
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

    public static List<LinkChain> createLinkChainList(List<Pair<Pair<String, String>,Pair<String, String>>> chains,
                                                      Map<PopResource, String> stackMap, Map<String, PopResource> popNodeMap){
        List<LinkChain> linkChainList = new ArrayList<LinkChain>();

        for (Pair<Pair<String, String>,Pair<String, String>> c : chains){
            Pair<String,String> left = c.getLeft();
            Pair<String,String> right = c.getRight();
            String leftNodeName = null;
            String rightNodeName = null;

            PopResource leftPop = popNodeMap.get(left.getLeft());
            PopResource rightPop = popNodeMap.get(right.getLeft());

            if(leftPop == null || rightPop == null)
                continue;

            String leftStack = stackMap.get(leftPop);
            String rightStack = stackMap.get(rightPop);

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

    public static List<String> getServerListFromHeatTemplate(HeatTemplate template) {

        List<String> popNodes = new ArrayList<String>();

        Collection<HeatResource> col = (Collection)template.getResources().values();
        for(HeatResource h: col) {
            if (h.getType().equals("OS::Nova::Server")) {
                String functionInstanceName = h.getResourceName();
                popNodes.add(h.getResourceName());
            }
        }
        return popNodes;
    }


    // Initialize JSON mapper

    static ObjectMapper mapper = new ObjectMapper(new JsonFactory());
    static SimpleModule module = new SimpleModule();

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
