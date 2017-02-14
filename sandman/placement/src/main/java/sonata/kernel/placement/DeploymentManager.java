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
import sonata.kernel.placement.net.LinkChain;
import sonata.kernel.placement.net.TranslatorChain;
import sonata.kernel.placement.net.TranslatorLoadbalancer.FloatingNode;
import sonata.kernel.placement.service.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Manages the deployed service
 * One service can be deployed, updated and removed
 */
public class DeploymentManager implements Runnable {

    final static Logger logger = Logger.getLogger(DeploymentManager.class);

    /**
     * Minimal time to wait between datacenter network in milliseconds.
     */
    static int defaultNetworkWaitMs = 1000;
    /**
     * Chaining rules currently in place.
     */
    static List<LinkChain> currentChaining = new ArrayList<LinkChain>();
    /**
     * Loadbalance rules currently in place.
     */
    static Map<LinkPort, LinkLoadbalance> currentLoadbalanceMap = new HashMap<LinkPort, LinkLoadbalance>();
    /**
     * Details about the current service instance.
     */
    static ServiceInstance currentInstance;
    /**
     * Descriptors used to instantiate current service.
     */
    static DeployServiceData currentDeployData;
    /**
     * List of datacenters currently in use.
     * For each of this datacenters a stack is deployed.
     */
    static List<PopResource> currentPops;
    /**
     * List of currently deployed servers.
     */
    static List<String> currentNodes;
    /**
     * Loadbalancer rule to connect the service with an ip that is accessable from the emulator host.
     */
    static FloatingNode inputFloatingNode = null;
    /**
     * List of vnf, port name pairs that are currently connected to the floating node.
     */
    static List<Pair<String,String>> currentFloatingPorts = new ArrayList<Pair<String,String>>();
    /**
     * Name for all stacks that belong to this service.
     * There is one stack per datacenter and all share the same name.
     */
    static String serviceStackName = null;
    /**
     * Type of scale message for the next fake scale operation
     */
    static MonitorMessage.SCALE_TYPE nextScale = null;

    /**
     * Main loop that receives control messages out of the @MessageQueue.
     * The loop can only be stopped by sending a terminate message.
     */
    public void run() {
        while (true) {

            try {
                logger.debug("Waiting for message");
                MessageQueue.MessageQueueData message = MessageQueue.get_deploymentQ().take();
                if (message.message_type == MessageQueue.MessageType.TERMINATE_MESSAGE) {

                    logger.info("Terminate Message");
                    tearDown();
                    return;

                } else if (message.message_type == MessageQueue.MessageType.DEPLOY_MESSAGE) {

                    MessageQueue.MessageQueueDeployData deployMessage = (MessageQueue.MessageQueueDeployData) message;
                    logger.info("Deploy Message - deploy index " + deployMessage.index);
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

    /**
     * Processes a deploy message by
     *  - Starting the @PlacementPlugin and call initialScaling
     *  - Creating Heat templates
     *  - Detecting necessary datacenter operations
     *  - Deploying the service and calling necessary datacenter
     *  - Starting monitoring
     * @param message Message containing the index of the service to deploy.
     */
    public static void deploy(MessageQueue.MessageQueueDeployData message) {

        try {

            message.responseId = 500;
            message.responseMessage = "Something went terribly wrong";

            if (currentInstance != null) {
                logger.info("Already deployed, undeploy first");
                message.responseId = 406;
                message.responseMessage = "Already deployed, undeploy first";
                return;
            }

            DatacenterManager.reset_resources();
            PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();
            PlacementPlugin plugin = PlacementPluginLoader.placementPlugin;

            DeployServiceData data = Catalogue.getPackagetoDeploy(message.index);
            String serviceName = data.getNsd().getName();
            logger.info("Deloying service: " + serviceName);

            ServiceInstance instance = plugin.initialScaling(data);
            List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources());

            // Maps function unique id -> function_instance
            Map<String, FunctionInstance> functionMap = new HashMap<String, FunctionInstance>();
            // Go through map: function name -> <function unique id -> function_instance>
            for (Map<String, FunctionInstance> map : instance.function_list.values()) {
                for (Map.Entry<String, FunctionInstance> entry : map.entrySet())
                    functionMap.put(entry.getValue().name, entry.getValue());
            }

            SimpleDateFormat format = new SimpleDateFormat("mmssSS");
            String timestamp = format.format(new Date());
            serviceStackName = timestamp + "-" + serviceName;

            List<FunctionMonitor> vnfMonitors = new ArrayList<FunctionMonitor>();

            Map<String, PopResource> popNodeMap = new HashMap<String, PopResource>();

            List<String> usedNodes = new ArrayList<String>();
            List<PopResource> allPops = config.getResources();
            List<PopResource> usedPops = new ArrayList<PopResource>();

            for (int i = 0; i < allPops.size(); i++) {
                PopResource pop = allPops.get(i);
                HeatTemplate template = templates.get(i);

                if (template == null)
                    continue;
                usedPops.add(pop);

                for (String nodeName : getServerListFromHeatTemplate(template)) {
                    // Populate maps and lists
                    popNodeMap.put(nodeName, pop);
                    usedNodes.add(nodeName);
                    // Prepare function monitors
                    FunctionMonitor monitor = new FunctionMonitor(pop, serviceStackName, nodeName);
                    monitor.instance = functionMap.get(nodeName);
                    vnfMonitors.add(monitor);
                }
            }

            // Execute deployment

            // Deploy stacks
            int lastStack = 0;
            boolean success = true;
            for (int i = 0; i < allPops.size(); i++) {
                PopResource pop = allPops.get(i);
                if (usedPops.contains(pop)) {
                    HeatTemplate template = templates.get(i);
                    String templateStr = templateToJson(template);
                    lastStack = i;
                    try {
                        TranslatorHeat.deployStack(pop, serviceStackName, templateStr);
                    } catch (Exception e) {
                        logger.info("Stack deployment failed for datacenter: " + pop.getPopName() + " Stack name: " + serviceStackName);
                        logger.error(e);
                        e.printStackTrace();
                        logger.debug(templateStr);
                        success = false;
                        break;
                    }

                    logger.debug("Create stack for datacenter: " + pop.getPopName() + " Stack name: " + serviceStackName);
                    logger.debug(templateStr);
                }
            }
            // In case of failure undeploy stacks
            if (success == false) {
                for (int i = 0; i < allPops.size() && lastStack > 0; i++) {
                    PopResource pop = allPops.get(i);
                    if (usedPops.contains(pop)) {
                        lastStack--;
                        try {
                            TranslatorHeat.undeployStack(pop, serviceStackName);
                        } catch (Exception e) {
                            logger.info("Stack undeployment failed for datacenter: " + pop.getPopName() + " Stack name: " + serviceStackName);
                            logger.error(e);
                            e.printStackTrace();
                        }
                    }
                }
                // Abort deployment
                cleanup();
                return;
            }


            // Chaining
            List<Pair<Pair<String, String>, Pair<String, String>>> create_chains = instance.get_create_chain();
            List<Pair<Pair<String, String>, Pair<String, String>>> delete_chains = instance.get_delete_chain();
            List<Pair<Pair<String, String>, List<String>>> custom_chains = instance.getCustomized_chains();
            List<LinkChain> create_link_chains = createLinkChainList(create_chains, popNodeMap);
            List<LinkChain> delete_link_chains = createLinkChainList(delete_chains, popNodeMap);
            // Add custom chain paths to chain to be created
            for (LinkChain chain : create_link_chains) {
                for (Pair<Pair<String, String>, List<String>> custom_chain : custom_chains) {
                    Pair<String, String> chain_nodes = custom_chain.getLeft();
                    if (chain_nodes.getLeft().equals(chain.srcPort.server) &&
                            chain_nodes.getRight().equals(chain.dstPort.server)) {
                        chain.path = custom_chain.getRight();
                    }
                }
            }
            createLinkLoadbalanceMap(currentLoadbalanceMap, currentChaining, create_link_chains, delete_link_chains);

            // Execute deployment
            try {
                chain(create_link_chains);
                logger.info("Chained");
            } catch(Exception e) {
                logger.info("Chaining failed");
                logger.error(e);
                e.printStackTrace();
            }
            networkWait(defaultNetworkWaitMs);

            try {
                unchain(delete_link_chains);
                logger.info("Unchained");
            } catch(Exception e) {
                logger.info("Unchaining failed");
                logger.error(e);
                e.printStackTrace();
            }
            networkWait(defaultNetworkWaitMs);

            // Add floating node and loadbalance input
            try {
                //FIXME: what datacenter to use? check vnf and decide for datacenter
                // vnf-name - port name
                List<Pair<String, String>> inputPorts = instance.get_create_input_lb_links();
                ArrayList<LinkPort> fnlist = new ArrayList<LinkPort>();
                for(Pair<String, String> inputPort: inputPorts) {
                    currentFloatingPorts.add(inputPort);
                    fnlist.add(new LinkPort(usedPops.get(0), serviceStackName, inputPort.getLeft(), inputPort.getRight()));
                }
                LinkLoadbalance lb = new LinkLoadbalance(usedPops.get(0), "floating", "foo", "bar", fnlist);
                inputFloatingNode = TranslatorLoadbalancer.floatingNode(lb);
            } catch(Exception e) {
                logger.info("Adding floating node failed");
                logger.error(e);
                e.printStackTrace();
            }
            networkWait(defaultNetworkWaitMs);

            // Loadbalancing
            try {
                unloadbalance(currentLoadbalanceMap.values());
                logger.info("Unloadbalanced");
            } catch(Exception e) {
                logger.info("Unloadbalancing failed");
                logger.error(e);
                e.printStackTrace();
            }
            networkWait(defaultNetworkWaitMs);

            try {
                loadbalance(currentLoadbalanceMap.values());
                logger.info("Loadbalanced");
            } catch(Exception e) {
                logger.info("Loadbalancing failed");
                logger.error(e);
                e.printStackTrace();
            }
            networkWait(defaultNetworkWaitMs);

            // Monitoring
            MonitorManager.addAndStartMonitor(vnfMonitors);

            currentInstance = instance;
            currentDeployData = data;
            currentPops = usedPops;
            currentNodes = usedNodes;

            message.responseId = 201;
            message.responseMessage = "Created";

        } catch (Exception e) {
            logger.error("Deployment failed", e);
            e.printStackTrace();
            // Abort deployment
            cleanup();
        } finally {
            synchronized (message) {
                message.notify();
            }
        }
    }

    /**
     * Processes an undeploy message by
     *  - Rewinding all network operations
     *  - Undeploy all stacks
     *  - Stopping monitoring
     *  - Cleaning up all of the service objects
     * @param message Message that represents the Undeploying command.
     */
    public static void undeploy(MessageQueue.MessageQueueUnDeployData message) {

        try {

            if (message != null) {
                message.responseId = 500;
                message.responseMessage = "Something went terribly wrong";
            }

            if (currentInstance != null) {
                try {
                    MonitorManager.stopAndRemoveAllMonitors();
                } catch (Exception e) {
                    logger.error(e);
                    e.printStackTrace();
                }

                try {
                    unchain(currentChaining);
                } catch (Exception e) {
                    logger.error(e);
                    e.printStackTrace();
                }
                networkWait(defaultNetworkWaitMs);

                // Loadbalancing
                try {
                    unloadbalance(currentLoadbalanceMap.values());
                } catch (Exception e) {
                    logger.error(e);
                    e.printStackTrace();
                }
                networkWait(defaultNetworkWaitMs);

                // Remove floating node
                try {
                    if(inputFloatingNode != null) {
                        TranslatorLoadbalancer.unFloatingNode(inputFloatingNode);
                    }
                } catch(Exception e) {
                    logger.error(e);
                    e.printStackTrace();
                }
                networkWait(defaultNetworkWaitMs);

                // Undeploy stacks
                for (PopResource pop : currentPops) {
                    try {
                        TranslatorHeat.undeployStack(pop, serviceStackName);
                        logger.info("Removed stack "+serviceStackName);
                    } catch (Exception e) {
                        logger.error(e);
                        e.printStackTrace();
                    }
                }
                networkWait(defaultNetworkWaitMs);
            }
            cleanup();

            if (message != null) {
                message.responseId = 200;
                message.responseMessage = "OK";
            }

        } catch(Exception e){
            logger.error("Undeployment failed");
            logger.error(e);
            e.printStackTrace();
        }
        finally {
            if (message != null) {
                synchronized (message) {
                    message.notify();
                }
            }
        }
    }

    /**
     * Removes all objects created for the deployed service.
     */
    protected static void cleanup() {
        currentInstance = null;
        currentDeployData = null;
        currentPops = null;
        currentNodes = null;
        currentLoadbalanceMap.clear();
        currentFloatingPorts.clear();
        inputFloatingNode = null;
        serviceStackName = null;
    }

    /**
     * Processes an update message by
     *  - Passing the monitoring data or fake scale command to the @PlacementPlugin
     *  If an update is necessary it
     *  - Detects the necessary changes
     *  - Rewinds old operations and undeploys unnecessary stacks
     *  - Deploys new stacks and executes new network operations
     *  - Updates monitoring
     * @param message Message that contains monitoring data or a fake scale command.
     */
    public static void monitor(MessageQueue.MessageQueueMonitorData message) {

        try {

            if (currentInstance == null)
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

            if (monitorMessage.type == MonitorMessage.SCALE_TYPE.SCALE_OUT)
                logger.info("Scale out");
            else if (monitorMessage.type == MonitorMessage.SCALE_TYPE.SCALE_IN)
                logger.info("Scale in");

            String serviceName = currentDeployData.getNsd().getName();

            List<HeatTemplate> templates = ServiceHeatTranslator.translatePlacementMappingToHeat(instance, config.getResources());

            Map<String, FunctionInstance> functionMap = new HashMap<String, FunctionInstance>();
            for (Map<String, FunctionInstance> map : instance.function_list.values()) {
                for (Map.Entry<String, FunctionInstance> entry : map.entrySet())
                    functionMap.put(entry.getValue().name, entry.getValue());
            }

            SimpleDateFormat format = new SimpleDateFormat("mmssSS");
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
            List<PopResource> removedStacks = new ArrayList<PopResource>();

            // Find out used/ old pops
            for (int i = 0; i < templates.size(); i++) {
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
            for (PopResource oldPop : currentPops) {
                if (!usedPops.contains(oldPop)) {
                    removedPops.add(oldPop);
                    removedStacks.add(oldPop);
                }
            }

            for (int i = 0; i < allPops.size(); i++) {
                PopResource pop = allPops.get(i);
                String popName = pop.getPopName();
                logger.debug("Check pop " + popName);



                HeatTemplate template = templates.get(i);
                if (template == null)
                    continue;

                for (String nodeName : getServerListFromHeatTemplate(template)) {
                    assert !popNodeMap.containsKey(nodeName) : "Vnf name " + nodeName + " is not unique! This should have never happened!";
                    popNodeMap.put(nodeName, pop);
                    usedNodes.add(nodeName);
                }
            }
            List<String> removedNodes = new ArrayList<String>();
            List<String> newNodes = new ArrayList<String>();
            for (String oldNode : currentNodes) {
                if (!usedNodes.contains(oldNode)) {
                    removedNodes.add(oldNode);
                }
            }
            for (String nodeName : usedNodes) {
                if (!currentNodes.contains(nodeName)) {
                    newNodes.add(nodeName);
                }
            }

            // Find out function monitor changes
            List<FunctionMonitor> addedFunctions = new ArrayList<FunctionMonitor>();
            List<FunctionMonitor> removedFunctions = new ArrayList<FunctionMonitor>();

            for (FunctionMonitor monitor : MonitorManager.monitors) {
                if (removedNodes.contains(monitor.function)) {
                    removedFunctions.add(monitor);
                    logger.debug("Removed monitor " + monitor.function);
                }
            }
            for (String nodeName : newNodes) {
                PopResource pop = popNodeMap.get(nodeName);
                FunctionMonitor monitor = new FunctionMonitor(pop, serviceStackName, nodeName);
                monitor.instance = functionMap.get(nodeName);
                addedFunctions.add(monitor);
                logger.debug("New monitor " + monitor.function);
            }

            // Chaining
            List<Pair<Pair<String, String>, Pair<String, String>>> create_chains = instance.get_create_chain();
            List<Pair<Pair<String, String>, Pair<String, String>>> delete_chains = instance.get_delete_chain();
            List<Pair<Pair<String, String>, List<String>>> custom_chains = instance.getCustomized_chains();
            List<LinkChain> create_link_chains = createLinkChainList(create_chains, popNodeMap);
            List<LinkChain> delete_link_chains = createLinkChainList(delete_chains, popNodeMap);
            // Add custom chain paths to chain to be created
            for (LinkChain chain : create_link_chains) {
                for (Pair<Pair<String, String>, List<String>> custom_chain : custom_chains) {
                    Pair<String, String> chain_nodes = custom_chain.getLeft();
                    if (chain_nodes.getLeft().equals(chain.srcPort.server) &&
                            chain_nodes.getRight().equals(chain.dstPort.server)) {
                        chain.path = custom_chain.getRight();
                    }
                }
            }

            // Floating loadbalancing
            List<Pair<String, String>> createLbLinks = instance.get_create_input_lb_links();
            List<Pair<String, String>> deleteLbLinks = instance.get_delete_input_lb_links();
            boolean updateFloatingLoadbalance = false;
            LinkLoadbalance newFloatingLbRule = null;
            if(createLbLinks.size()>0 || deleteLbLinks.size()>0) {

                // vnf-name - port name
                ArrayList<Pair<String,String>> newFloatingPorts = new ArrayList<Pair<String,String>>();

                // check if old ports are to be deleted
                for(Pair<String, String> oldPort: currentFloatingPorts){
                    for(Pair<String, String> delPort: deleteLbLinks) {
                        if(oldPort.getLeft().equals(delPort.getLeft()) &&
                                oldPort.getRight().equals(delPort.getRight())) {
                            break;
                        }
                    }
                    // old port not in delete list
                    newFloatingPorts.add(oldPort);
                }
                // add new ports
                newFloatingPorts.addAll(createLbLinks);

                // create new floating ports rule
                ArrayList<LinkPort> fnlist = new ArrayList<LinkPort>();
                for(Pair<String, String> inputPort: newFloatingPorts) {
                    fnlist.add(new LinkPort(usedPops.get(0), serviceStackName, inputPort.getLeft(), inputPort.getRight()));
                }
                currentFloatingPorts = newFloatingPorts;
                newFloatingLbRule = new LinkLoadbalance(usedPops.get(0), "floating", "foo", "bar", fnlist);
                updateFloatingLoadbalance = true;
            }

            // Execute changes

            // Remove old resources

            try {
                unloadbalance(currentLoadbalanceMap.values());
                logger.info("Unloadbalanced");
            } catch (Exception e) {
                logger.info("Unloadbalance failed");
                logger.error(e);
                e.printStackTrace();
            }
            networkWait(defaultNetworkWaitMs);

            createLinkLoadbalanceMap(currentLoadbalanceMap, currentChaining, create_link_chains, delete_link_chains);

            try {
                unchain(delete_link_chains);
                logger.info("Unchained");
            } catch (Exception e) {
                logger.info("Unchaining failed");
                logger.error(e);
                e.printStackTrace();
            }
            networkWait(defaultNetworkWaitMs);

            // FIXME: DELETE ALSO CURRENT CHAINS
            ArrayList<LinkChain> oldChains = new ArrayList<LinkChain>();
            oldChains.addAll(currentChaining);
            try {
                unchain(currentChaining);
                logger.info("Unchained current");
            } catch (Exception e) {
                logger.info("Unchaining current failed");
                logger.error(e);
                e.printStackTrace();
            }
            networkWait(defaultNetworkWaitMs);

            // FIXME: DELETE ALSO CURRENT CHAINS

            // Undeploy removed stacks
            for (PopResource removedStackPop : removedStacks) {
                try {
                    TranslatorHeat.undeployStack(removedStackPop, serviceStackName);
                    logger.info("Undeploy stack for datacenter: " + removedStackPop.getPopName() + " Stack name: " + serviceStackName);
                } catch (Exception e) {
                    logger.info("Stack undeployment failed for datacenter: " + removedStackPop.getPopName() + " Stack name: " + serviceStackName);
                    logger.error(e);
                    e.printStackTrace();
                }
            }
            networkWait(defaultNetworkWaitMs);

            // Remove of the floating node loadbalancing
            if(updateFloatingLoadbalance == true) {
                try {
                    TranslatorLoadbalancer.unFloatingNode(inputFloatingNode);
                    inputFloatingNode = null;
                    logger.info("Floating lb remove successful");
                } catch (Exception e) {
                    logger.info("Floating lb remove failed");
                    logger.error(e);
                    e.printStackTrace();
                }
                networkWait(defaultNetworkWaitMs);
            }

            // Update stacks

            for (int i = 0; i < allPops.size(); i++) {
                PopResource pop = allPops.get(i);
                if (oldPops.contains(pop)) {
                    HeatTemplate template = templates.get(i);
                    String templateStr = templateToJson(template);
                    try {
                        TranslatorHeat.updateStack(pop, serviceStackName, templateStr);
                        logger.debug("Update stack for datacenter: " + pop.getPopName() + " Stack name: " + serviceStackName);
                    } catch (Exception e) {
                        logger.info("Stack update failed for datacenter: " + pop.getPopName() + " Stack name: " + serviceStackName);
                        logger.error(e);
                        e.printStackTrace();
                    }
                    logger.debug(templateStr);
                    networkWait(defaultNetworkWaitMs);
                }
            }

            // Add new resources

            // Deploy new stacks

            for (int i = 0; i < allPops.size(); i++) {
                PopResource pop = allPops.get(i);
                if (newPops.contains(pop)) {
                    HeatTemplate template = templates.get(i);
                    String templateStr = templateToJson(template);
                    try {
                        TranslatorHeat.deployStack(pop, serviceStackName, templateStr);
                        logger.debug("Create stack for datacenter: " + pop.getPopName() + " Stack name: " + serviceStackName);

                    } catch (Exception e) {
                        logger.info("Stack deployment failed for datacenter: " + pop.getPopName() + " Stack name: " + serviceStackName);
                        logger.error(e);
                        e.printStackTrace();

                    }
                    logger.debug(templateStr);
                    networkWait(defaultNetworkWaitMs);
                }
            }

            // FIXME: Add also old chains

            try {
                chain(oldChains);
                logger.info("Chaining current");
            } catch (Exception e) {
                logger.info("Chaining current failed");
                logger.error(e);
                e.printStackTrace();
            }
            networkWait(defaultNetworkWaitMs);

            // FIXME: Add also old chains

            try {
                chain(create_link_chains);
                logger.info("Chaining");
            } catch (Exception e) {
                logger.info("Chaining failed");
                logger.error(e);
                e.printStackTrace();
            }
            networkWait(defaultNetworkWaitMs);

            try {
                loadbalance(currentLoadbalanceMap.values());
                logger.info("Loadbalanced");
            } catch (Exception e) {
                logger.info("Loadbalancing failed");
                logger.error(e);
                e.printStackTrace();
            }
            networkWait(defaultNetworkWaitMs);

            // Add new floating node loadbalancing
            if(updateFloatingLoadbalance == true) {
                try {
                    inputFloatingNode = TranslatorLoadbalancer.floatingNode(newFloatingLbRule);
                    logger.info("Add floating lb rule successful");
                } catch (Exception e) {
                    logger.info("Add floating lb rule failed");
                    logger.error(e);
                    e.printStackTrace();
                }
                networkWait(defaultNetworkWaitMs);
            }

            // Monitoring
            MonitorManager.updateMonitors(addedFunctions, removedFunctions);

            currentInstance = instance;
            currentPops = usedPops;
            currentNodes = usedNodes;
            currentPops = usedPops;
            logger.info("Service update finished");

        } catch(Exception e) {
            logger.error("Monitor update failed");
            logger.error(e);
            e.printStackTrace();
        }
    }

    /**
     * Merges single chaining rules to loadbalancing rules or replaces unnecessary loadbalancing rules with chaining rules.
     *
     * It behaves as follows:
     *  - Port to port chaining rules where the ports are involved in only this rule stay as they are.
     *  - Chaining rules where one port is the destination of several other ports stay as they are.
     *    The emulator chaining manages this kind of aggregation.
     *  - Chaining rules where one port is connected to several destination ports are replaced with loadbalancing rules.
     *  - Loadbalancing rules with only two involved ports are replaced with one chaining rule respectively.
     *  (A list of loadbalancing rules that should be deleted is not necessary since the update method removes all
     *  loadbalancing rules before executing the updated rules.)
     *
     * @param lbMap Map mapping Ports to Loadbalancing rules that contain the port as source port.
     * @param currentChains List of currently deployed chaining rules.
     * @param createChains List of chaining rules that should be created.
     * @param deleteChains List of chaining rules that should be deleted.
     */
    public static void createLinkLoadbalanceMap(Map<LinkPort, LinkLoadbalance> lbMap, List<LinkChain> currentChains,
                List<LinkChain> createChains, List<LinkChain> deleteChains) {
        // Add current Chains (if not already persistent)
        for (LinkChain chain : currentChains) {
            if (!lbMap.containsKey(chain.srcPort)) {
                List<LinkPort> dstPorts = new ArrayList<LinkPort>();
                dstPorts.add(chain.dstPort);
                LinkLoadbalance lb = new LinkLoadbalance(chain.srcPort, dstPorts);
                lbMap.put(chain.srcPort, lb);
            } else {
                // Already in map, nothing to do...
            }
        }
        // Remove chains, that are to be deleted, from loadbalancing rules
        for (LinkChain chain : deleteChains) {
            if (lbMap.containsKey(chain.srcPort)) {
                LinkLoadbalance lb = lbMap.get(chain.srcPort);
                lb.dstPorts.remove(chain.dstPort);
                // Keep empty loadbalance objects in case they will be used in creation loop later
            } else {
                // Nothing to do...
            }
        }
        // Add chains, that are to be added, to loadbalancing rules
        for (LinkChain chain : createChains) {
            if (!lbMap.containsKey(chain.srcPort)) {
                List<LinkPort> dstPorts = new ArrayList<LinkPort>();
                dstPorts.add(chain.dstPort);
                LinkLoadbalance lb = new LinkLoadbalance(chain.srcPort, dstPorts);
                lbMap.put(chain.srcPort, lb);
            } else {
                LinkLoadbalance lb = lbMap.get(chain.srcPort);
                if (!lb.dstPorts.contains(chain.dstPort))
                    lb.dstPorts.add(chain.dstPort);
            }
        }
        // Remove empty loadbalancing rules, or rules concerning only one destination port
        List<LinkLoadbalance> lbList = new ArrayList<LinkLoadbalance>();
        lbList.addAll(lbMap.values());
        for (LinkLoadbalance lb : lbList) {
            if (lb.dstPorts.size() == 0) {
                // Chain no more in use, just remove the lb rule
                lbMap.remove(lb.srcPort);
            } else
            if (lb.dstPorts.size() == 1) {
                // Remove the lb rule
                lbMap.remove(lb.srcPort);
                // Check if chaining rule is in current chaining rules or createChains, if not create new chaining rule
                LinkChain newChain = new LinkChain(lb.srcPort, lb.dstPorts.get(0));
                if(!currentChains.contains(newChain) && !createChains.contains(newChain))
                    createChains.add(newChain);
            }
            else {
                // Check for each chain,
                // if there is a create chain entry remove the create chain entry because the lb rule take care of this chain
                // if there is a current chain entry remove the current chain entry because the lb rule will replace it
                for(LinkPort dstPort : lb.dstPorts) {
                    LinkChain dummyChain = new LinkChain(lb.srcPort, dstPort);
                    if (createChains.contains(dummyChain)) {
                        // actually you do not need the contains check
                        createChains.remove(dummyChain);
                    }
                    if (currentChains.contains(dummyChain)) {
                        deleteChains.add(dummyChain);
                    }
                }
            }
        }
    }

    /**
     * Utility method that converts Pairs of vnf,port names to LinkChain objects.
     * @param chains List of Pairs of vnf,port names that represent chains.
     * @param popNodeMap Map mapping vnf names to datacenters that contain the vnf.
     * @return The List of Chaining rules created from the Chaining Pairs.
     */
    public static List<LinkChain> createLinkChainList(List<Pair<Pair<String, String>, Pair<String, String>>> chains,
                                                      Map<String, PopResource> popNodeMap) {
        List<LinkChain> linkChainList = new ArrayList<LinkChain>();

        for (Pair<Pair<String, String>, Pair<String, String>> c : chains) {
            Pair<String, String> left = c.getLeft();
            Pair<String, String> right = c.getRight();
            String leftNodeName = null;
            String rightNodeName = null;

            PopResource leftPop = popNodeMap.get(left.getLeft());
            PopResource rightPop = popNodeMap.get(right.getLeft());

            if (leftPop == null || rightPop == null)
                continue;

            String leftStack = serviceStackName;
            String rightStack = serviceStackName;

            linkChainList.add(new LinkChain(leftPop, leftStack, left.getLeft(), left.getRight(),
                    rightPop, rightStack, right.getLeft(), right.getRight()));
        }
        return linkChainList;
    }

    /**
     * Executes a list of chaining rules.
     * If a chaining rule contains a custom path the Custom Chaining API will be used.
     * @param chains List of chaining rules.
     */
    public static void chain(List<LinkChain> chains) {
        for (LinkChain chain : chains) {
            if (chain.path != null) {
                TranslatorChain.chainCustom(chain);
            } else {
                TranslatorChain.chain(chain);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            currentChaining.add(chain);
            networkWait(defaultNetworkWaitMs);
        }
    }

    /**
     * Deletes a list of chaining rules from the respective datacenters.
     * Custom chaining rules are deleted using the same API.
     * @param chains List chaining rules to be deleted.
     */
    public static void unchain(List<LinkChain> chains) {
        for (LinkChain chain : chains) {
            TranslatorChain.unchain(chain);
            if (chains != currentChaining)
                currentChaining.remove(chain);
            networkWait(defaultNetworkWaitMs);
        }
        if (chains == currentChaining)
            currentChaining.clear();
    }

    /**
     * Executes a list of loadbalancing rules.
     * @param balances List of loadbalancing rules
     */
    public static void loadbalance(Collection<LinkLoadbalance> balances) {
        for (LinkLoadbalance balance : balances) {
            // No loadbalancing for only
            if (balance.dstPorts.size() > 1)
                TranslatorLoadbalancer.loadbalance(balance);
            networkWait(defaultNetworkWaitMs);
        }
    }

    /**
     * Deletes a list of loadbalancing rules.
     * @param balances List of loadbalancing rules to be deleted
     */
    public static void unloadbalance(Collection<LinkLoadbalance> balances) {
        for (LinkLoadbalance balance : balances)
            TranslatorLoadbalancer.unloadbalance(balance.srcPort);
    }

    /**
     * Undeploys the current service and shuts down monitoring
     * in case of application end.
     */
    public static void tearDown() {
        undeploy(null);
        MonitorManager.closeConnectionPool();
    }

    // Utility
    /**
     * Sleeps for given amount of milliseconds to wait inbetween of network operations.
     * @param msSleep amount of time to sleep in milliseconds
     */
    public static void networkWait(int msSleep){
        try {
            Thread.sleep(msSleep);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Extracts the list of servers from a HeatTemplate.
     * @param template A HeatTemplate
     * @return The list of servers defined in the given HeatTemplate
     */
    public static List<String> getServerListFromHeatTemplate(HeatTemplate template) {

        List<String> popNodes = new ArrayList<String>();

        Collection<HeatResource> col = (Collection) template.getResources().values();
        for (HeatResource h : col) {
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

    /**
     * Maps a HeatTemplate to a JSON String.
     * @param template The HeatTemplate to be mapped
     * @return A JSON String representing the given HeatTemplate
     */
    public static String templateToJson(HeatTemplate template) {
        try {
            return mapper.writeValueAsString(template);
        } catch (JsonProcessingException e) {
            return null;
        }
    }


}
