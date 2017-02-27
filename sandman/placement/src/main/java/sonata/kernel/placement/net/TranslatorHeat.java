package sonata.kernel.placement.net;

import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.heat.Stack;
import org.openstack4j.openstack.OSFactory;
import sonata.kernel.placement.config.PopResource;

import java.util.List;

/**
 * Utility functions to deploy Stacks on a son-emu emulator using the OpenStack HEAT REST API.
 */
public class TranslatorHeat {

    /**
     * Deploys a stack on a datacenter by using OpenStack Heat API.
     * @param pop Datacenter to deploy the stack to.
     * @param stackName Name of the new stack.
     * @param templateJsonString Heat template as JSON String.
     */
    public static void deployStack(PopResource pop, String stackName, String templateJsonString) {
        OSClient.OSClientV2 os = OSFactory.builderV2()
                .endpoint(pop.getEndpoint())
                .credentials(pop.getUserName(), pop.getPassword())
                .tenantName(pop.getTenantName())
                .authenticate();

        Stack stack = os.heat().stacks().create(Builders.stack()
                .name(stackName)
                .template(templateJsonString)
                .timeoutMins(5L).build());
    }

    /**
     * Updates stack on a datacenter.
     * @param pop Datacenter containing the stack in need of an update.
     * @param stackName Name of the stack that should be updated.
     * @param templateJsonString Heat template a JSON String.
     */
    public static void updateStack(PopResource pop, String stackName, String templateJsonString) {
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
    }

    /**
     * Removes a stack from a datacenter.
     * @param pop Datacenter to free from the burden of the stack.
     * @param stackName Name of the stack that should be removed.
     */
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
}
