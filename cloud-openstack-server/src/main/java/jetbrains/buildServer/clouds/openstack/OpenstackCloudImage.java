package jetbrains.buildServer.clouds.openstack;

import com.jcabi.log.VerboseRunnable;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OpenstackCloudImage implements CloudImage {
    @NotNull private final String imageId;
    @NotNull private final String imageName;
    @NotNull private final String openstackImageName;
    @NotNull private final String flavorName;
    @NotNull private final OpenstackApi openstackApi;
    @NotNull private final CreateServerOptions options;
    @Nullable private final String userScriptPath;
    @NotNull private final ServerPaths serverPaths;
    @NotNull private final ScheduledExecutorService executor;

    @NotNull private final Map<String, OpenstackCloudInstance> instances = new ConcurrentHashMap<String, OpenstackCloudInstance>();
    @NotNull private final IdGenerator instanceIdGenerator = new IdGenerator();
    @Nullable private final CloudErrorInfo errorInfo;

    public OpenstackCloudImage(@NotNull final String imageId,
                               @NotNull final String imageName,
                               @NotNull final OpenstackApi openstackApi,
                               @NotNull final String openstackImageName,
                               @NotNull final String flavorId,
                               @NotNull final CreateServerOptions options,
                               @Nullable final String userScriptPath,
                               @NotNull final ServerPaths serverPaths,
                               @NotNull final ScheduledExecutorService executor) {
        this.imageId = imageId;
        this.imageName = imageName;
        this.openstackApi = openstackApi;
        this.openstackImageName = openstackImageName;
        this.flavorName = flavorId;
        this.options = options;
        this.userScriptPath = userScriptPath;
        this.serverPaths = serverPaths;
        this.executor = executor;

        this.errorInfo = null;  //FIXME: need to use this, really.

        this.executor.scheduleWithFixedDelay(new VerboseRunnable(new Runnable() {
            public void run() {
                final Collection<OpenstackCloudInstance> instances = (Collection<OpenstackCloudInstance>) getInstances();
                for (OpenstackCloudInstance instance : instances) {
                    instance.updateStatus();
                    if (instance.getStatus() == InstanceStatus.STOPPED || instance.getStatus() == InstanceStatus.ERROR)
                        forgetInstance(instance);
                }
            }}, true ), 3, 3, TimeUnit.SECONDS);
    }

    @NotNull
    public ServerApi getNovaApi() {
        return openstackApi.getNovaApi();
    }

    private void forgetInstance(@NotNull final OpenstackCloudInstance instance) {
        instances.remove(instance.getInstanceId());
    }

    @NotNull
    public CreateServerOptions getImageOptions() {
        return options;
    }

    @NotNull
    public String getOpenstackImageId() {
        return openstackApi.getImageIdByName(openstackImageName);
    }

    @NotNull
    public String getFlavorId() {
        return openstackApi.getFlavorIdByName(flavorName);
    }

    @NotNull
    public String getId() {
        return imageId;
    }

    @NotNull
    public String getName() {
        return imageName;
    }

    @NotNull
    public String getOpenstackImageName() {
        return this.openstackImageName;
    }

    @NotNull
    public String getOpenstackFalvorName() {
        return this.flavorName;
    }

    @Nullable
    public String getUserScriptPath() {
        return userScriptPath;
    }

    @NotNull
    public Collection<? extends OpenstackCloudInstance> getInstances() {
        return Collections.unmodifiableCollection(instances.values());
    }

    @Nullable
    public OpenstackCloudInstance findInstanceById(@NotNull final String instanceId) {
        return instances.get(instanceId);
    }

    @Nullable
    @Override
    public Integer getAgentPoolId() {
        return null;
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    @NotNull
    public synchronized OpenstackCloudInstance startNewInstance(@NotNull final CloudInstanceUserData data) {
        final String instanceId = instanceIdGenerator.next();
        final OpenstackCloudInstance instance = new OpenstackCloudInstance(this, instanceId, serverPaths, executor);

        instances.put(instanceId, instance);
        instance.start(data);

        return instance;
    }

    void dispose() {
        for (final OpenstackCloudInstance instance : instances.values()) {
            instance.terminate();
        }
        instances.clear();
        executor.shutdown();
    }
}
