package sonata.kernel.placement.service;

import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.DeployServiceData;

import java.util.ArrayList;
import java.util.List;

public class DemoPlacementPlugin implements PlacementPlugin {
    final static Logger logger = Logger.getLogger(DemoPlacementPlugin.class);

    ServiceInstanceManager instance_manager;

    // only do scaling decisions if this counter is 0
    int scaleDelayCounter = 0;
    // points to last added vmir instance number (e.g. first: "vmir1")
    int vmirCount = 1;

    @Override
    public ServiceInstance initialScaling(DeployServiceData serviceData) {
        logger.info("Initial Scaling");



        PlacementManager pm = new PlacementManager();
        ServiceInstance inst = pm.InitializeService(serviceData);

        return inst;

    }



    @Override
    public ServiceInstance updateScaling(DeployServiceData serviceData, ServiceInstance instance, MonitorMessage message) {
        logger.info("Update Scaling");

        PlacementManager pm = new PlacementManager(instance);

        if (scaleDelayCounter > 0)
            scaleDelayCounter--;

        List<String> overload_l = new ArrayList<String>();
        List<String> underload_l = new ArrayList<String>();

        boolean scaleout_vmir = false;
        boolean scalein_vmir = false;

        try {
            ComputeMetrics c_metrics = new ComputeMetrics(instance, message.stats_history);
            c_metrics.compute_vnf_load(overload_l, underload_l);
        } catch (Exception e){
            e.printStackTrace();
        }
        for (String on:overload_l) {

            if ("vnf_vmir".equals(on)){
                System.out.println(">>>>>>>>>>>>>> OVERLOAD    "+on);
                scaleout_vmir = true;
            }
        }
        for (String on:underload_l) {

            if ("vnf_vmir".equals(on)){
                System.out.println(">>>>>>>>>>>>>> UNDERLOAD    "+on);
                // deactivate scale in for now
                //scalein_vmir = true;
            }
        }

        if (message.type == MonitorMessage.SCALE_TYPE.SCALE_OUT) {
            scaleout_vmir = true;
            scaleDelayCounter = 0;
        } else if (message.type == MonitorMessage.SCALE_TYPE.SCALE_IN) {
            scalein_vmir = true;
            scaleDelayCounter = 0;
        }

        if (scaleout_vmir == true && scaleDelayCounter == 0) {

            System.out.println(">>>>>>>>>>>>>> Scaling out vmir, new vmir"+(vmirCount+1));

            String newvnf = pm.AddNetworkFunctionInstance("vnf_vmir", "dc2");

            pm.AddVirtualLink( newvnf, "vdir1", null);

            ServiceInstance instance_t = pm.GetServiceInstance();

            vmirCount++;

            scaleDelayCounter = 15;

            return instance_t;
        } else
        if (scalein_vmir == true && vmirCount>1 && scaleDelayCounter == 0) {

            System.out.println(">>>>>>>>>>>>>> Scaling in vmir, last vmir"+(vmirCount));

            pm.DeleteNetworkFunctionInstance("vmir"+vmirCount);

            ServiceInstance instance_t = pm.GetServiceInstance();

            vmirCount--;

            scaleDelayCounter = 15;

            return instance_t;

        }

        return null;
    }

}
