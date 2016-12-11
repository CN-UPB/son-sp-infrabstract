package sonata.kernel.placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.log4j.Logger;
import sonata.kernel.placement.monitor.MonitorHistory;
import sonata.kernel.placement.monitor.MonitorStats;
import sonata.kernel.placement.service.MonitorMessage;


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
    public static class MessageQueueUnDeployData extends MessageQueueData{

        public volatile String responseMessage = null;
        public volatile int responseId = -1;

        public MessageQueueUnDeployData(){
            super(MessageType.UNDEPLOY_MESSAGE);
        }
    }
    public static class MessageQueueMonitorData extends MessageQueueData{

        public final Map<String, MonitorStats> statsMap;
        public final Map<String,List<MonitorStats>> statsHistoryMap;

        public MonitorMessage.SCALE_TYPE fakeScaleType = null;

        public MessageQueueMonitorData(Map<String, MonitorStats> statsMap, Map<String,List<MonitorStats>> statsHistoryMap){
            super(MessageType.MONITOR_MESSAGE);
            this.statsMap = statsMap;
            this.statsHistoryMap = statsHistoryMap;
        }
    }
}
