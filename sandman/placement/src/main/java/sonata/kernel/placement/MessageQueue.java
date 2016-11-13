package sonata.kernel.placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.log4j.Logger;

//Modify them as requrired.
enum MessageType {
    DEPLOY_MESSAGE, UNDEPLOY_MESSAGE, TERMINATE_MESSAGE
}
class MessageQueueData
{
	final static Logger logger = Logger.getLogger(MessageQueueData.class);
    MessageType message_type;

    public MessageQueueData(MessageType message_type) {
        this.message_type = message_type;
    }

}
class MessageQueueDeployData extends MessageQueueData{

    final public String deploy_path;

    public MessageQueueDeployData(String deploy_path){
        super(MessageType.DEPLOY_MESSAGE);
        this.deploy_path = deploy_path;
    }
}

class MessageQueue
{
	final static Logger logger = Logger.getLogger(MessageQueue.class);
    private static BlockingQueue<MessageQueueData> deploymentQ = new LinkedBlockingDeque<MessageQueueData>();

    static public BlockingQueue<MessageQueueData> get_deploymentQ()
    {
        return deploymentQ;
    }
}
