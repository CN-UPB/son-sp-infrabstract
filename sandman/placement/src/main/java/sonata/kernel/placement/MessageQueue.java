package sonata.kernel.placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.log4j.Logger;
import sonata.kernel.placement.monitor.MonitorHistory;


public class MessageQueue
{

    public enum MessageType {
        DEPLOY_MESSAGE, UNDEPLOY_MESSAGE, TERMINATE_MESSAGE, MONITOR_MESSAGE
    }

	final static Logger logger = Logger.getLogger(MessageQueue.class);
    private static BlockingQueue<MessageQueueData> deploymentQ = new LinkedBlockingDeque<MessageQueueData>();

    static public BlockingQueue<MessageQueueData> get_deploymentQ()
    {
        return deploymentQ;
    }

    public static class MessageQueueData
    {
        final static Logger logger = Logger.getLogger(MessageQueueData.class);
        MessageType message_type;

        public MessageQueueData(MessageType message_type) {
            this.message_type = message_type;
        }

    }
    public static class MessageQueueDeployData extends MessageQueueData{

        public final int index;
        public volatile String responseMessage = null;
        public volatile int responseId = -1;

        public MessageQueueDeployData(int index){
            super(MessageType.DEPLOY_MESSAGE);
            this.index = index;
        }
    }
    public static class MessageQueueMonitorData extends MessageQueueData{
        public final List<MonitorHistory> history;
        public MessageQueueMonitorData(List<MonitorHistory> history){
            super(MessageType.MONITOR_MESSAGE);
            this.history = history;
        }
    }
}
