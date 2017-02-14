package sonata.kernel.placement;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.log4j.Logger;
import sonata.kernel.placement.monitor.MonitorStats;
import sonata.kernel.placement.service.MonitorMessage;

/**
 * Holds queue for message passing between several components
 */
public class MessageQueue
{
    /**
     * Types of messages
     */
    public enum MessageType {
        /**
         * Used to deploy a service
         */
        DEPLOY_MESSAGE,
        /**
         * Used to undeploy a service
         */
        UNDEPLOY_MESSAGE,
        /**
         * Used to terminate the DeploymentManager in case of application shutdown
         */
        TERMINATE_MESSAGE,
        /**
         * Used to transfer monitoring data
         */
        MONITOR_MESSAGE
    }

	final static Logger logger = Logger.getLogger(MessageQueue.class);

    /**
     * Queue that buffer incoming messages for the DeploymentManager
     */
    private static BlockingQueue<MessageQueueData> deploymentQ = new LinkedBlockingDeque<MessageQueueData>();
    /**
     * Returns the queue for incoming DeploymentManager messages
     * @return
     */
    static public BlockingQueue<MessageQueueData> get_deploymentQ()
    {
        return deploymentQ;
    }

    /**
     * Message send over MessageQueues
     */
    public static class MessageQueueData
    {
        /**
         * The message's type
         */
        MessageType message_type;

        /**
         * Create a message with the given type
         * @param message_type
         */
        public MessageQueueData(MessageType message_type) {
            this.message_type = message_type;
        }
    }

    /**
     * Deploy message
     */
    public static class MessageQueueDeployData extends MessageQueueData{

        /**
         * Index of the service in @Catalogue to deploy
         */
        public final int index;
        /**
         * HTTP Status Message to indicate if deployment was successful
         */
        public volatile String responseMessage = null;
        /**
         * HTTP Status Code to indicate if deployment was successful
         */
        public volatile int responseId = -1;

        /**
         * Create a deploy message
         * @param index index of service to deploy
         */
        public MessageQueueDeployData(int index){
            super(MessageType.DEPLOY_MESSAGE);
            this.index = index;
        }
    }

    /**
     * Undeploy message
     */
    public static class MessageQueueUnDeployData extends MessageQueueData{

        /**
         * HTTP Status Message to indicate if undeployment was successful
         */
        public volatile String responseMessage = null;
        /**
         * HTTP Status Code to indicate if undeployment was successful
         */
        public volatile int responseId = -1;

        /**
         * Creates a undeploy message
         */
        public MessageQueueUnDeployData(){
            super(MessageType.UNDEPLOY_MESSAGE);
        }
    }

    /**
     * Monitor message
     */
    public static class MessageQueueMonitorData extends MessageQueueData{

        /**
         * Maps Vnf name to list of monitoring data
         * Maybe null if fakeScaleType is given.
         */
        public final Map<String,List<MonitorStats>> statsHistoryMap;

        /**
         * Type of fake scaling
         * Maybe null if statsHistoryMap is given.
         */
        public MonitorMessage.SCALE_TYPE fakeScaleType = null;

        /**
         * Creates a new monitor message
         * @param statsHistoryMap
         */
        public MessageQueueMonitorData(Map<String,List<MonitorStats>> statsHistoryMap){
            super(MessageType.MONITOR_MESSAGE);
            this.statsHistoryMap = statsHistoryMap;
        }
    }
}
