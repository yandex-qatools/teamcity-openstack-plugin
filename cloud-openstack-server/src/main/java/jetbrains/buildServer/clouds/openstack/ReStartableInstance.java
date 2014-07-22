package jetbrains.buildServer.clouds.openstack;

import jetbrains.buildServer.clouds.CloudConstants;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;


public class ReStartableInstance extends OpenstackCloudInstance {
    public ReStartableInstance(@NotNull String instanceId,
                               @NotNull OpenstackCloudImage image,
                               @NotNull final ScheduledExecutorService executor) {
        super(image, instanceId, executor);
    }

    @Override
    public boolean isRestartable() {
        return true;
    }

    @Override
    protected void cleanupStoppedInstance() {
        //NOP, meaning it could start again
    }

    @Override
    public void start(@NotNull CloudInstanceUserData data) {
        data.setAgentRemovePolicy(CloudConstants.AgentRemovePolicyValue.Unauthorize);
        super.start(data);
    }
}
