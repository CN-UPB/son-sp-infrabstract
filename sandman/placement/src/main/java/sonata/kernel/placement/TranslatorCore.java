package sonata.kernel.placement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sonata.kernel.placement.config.PlacementConfig;
import java.io.File;

import sonata.kernel.placement.config.PlacementConfigLoader;
import sonata.kernel.placement.service.PlacementPluginLoader;
import java.io.IOException;

/**
 * The start component of the translator.
 * Starts all the other necessary components, as the REST Api and the deployment component.
 */
public class TranslatorCore {

    final static Logger logger = LoggerFactory.getLogger(TranslatorCore.class);
    /**
     * Instance object of the deployment manager
     */
    static DeploymentManager deployment;
    /**
     * Thread that hosts the deployment manager
     */
    static Thread deploymentThread;

    public TranslatorCore() {
    }

    public static void main(String[] args) throws InterruptedException {

        // Load configuration
    	logger.info("Loading Configurations");
        logger.info("Current path: "+new File("").getAbsolutePath());
        PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();
        Catalogue.loadInternalFunctions();

        // Load placement plugin
        logger.info("Loading placement plugins");
        PlacementPluginLoader.loadPlacementPlugin(config.pluginPath,config.placementPlugin);
        logger.info("Loaded placement-plugin: "+PlacementPluginLoader.placementPluginClass.getName());

        DatacenterManager.initialize();
        // Start servers
        try {

            logger.info("Starting Gatekeeper Server");
            new RestInterfaceServerApi(config.restApi.getServerIp(), config.restApi.getPort()).start();

            logger.info("Starting DeploymentManager");
            deployment = new DeploymentManager();
            deploymentThread = new Thread(deployment, "DeploymentManagerThread");
            deploymentThread.start();

            Runtime.getRuntime().addShutdownHook(new Thread(new Cleanup()));

            deploymentThread.join();

          } catch (IOException ioe) {
            logger.error("Encountered exception", ioe);
        }

    }

    /**
     * Cleanup on shutdown
     */
    public static class Cleanup implements Runnable{
        public void run(){

            logger.info("Cleaning up for shutdown");
            logger.info("Terminating DeploymentManager ...");
            MessageQueue.get_deploymentQ().add(new MessageQueue.MessageQueueData(MessageQueue.MessageType.TERMINATE_MESSAGE));

            try {
                deploymentThread.join(60000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(deploymentThread.isAlive())
                logger.info("Deployment still running - Turning off anyway");
            else
                logger.info("Deployment stopped");

            logger.info("Cleaning up finished");
        }
    }
}