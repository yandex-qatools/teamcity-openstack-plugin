package jetbrains.buildServer.clouds.openstack;

import jetbrains.buildServer.clouds.CloudConstants;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;

public class OneUseOpenstackCloudInstance extends OpenstackCloudInstance {

    public OneUseOpenstackCloudInstance(@NotNull final String instanceId,
                                        @NotNull final OpenstackCloudImage image,
                                        @NotNull final ScheduledExecutorService executor) {
        super(image, instanceId, executor);
    }

//    @Override
//    public boolean isRestartable() {
//        return false;
//    }

    @Override
    protected void cleanupStoppedInstance() {
        getImage().forgetInstance(this);
        //FileUtil.delete(getBaseDir());
    }

    @Override
    public void start(@NotNull CloudInstanceUserData data) {
        data.setAgentRemovePolicy(CloudConstants.AgentRemovePolicyValue.RemoveAgent);
        super.start(data);
    }
}
