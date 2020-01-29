package jetbrains.buildServer.clouds.openstack;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.jcabi.log.VerboseRunnable;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.serverSide.ServerPaths;

public class OpenstackCloudImage implements CloudImage {

    @NotNull
    private final OpenstackApi openstackApi;
    @NotNull
    private final String imageId;
    @NotNull
    private final String imageName;
    @NotNull
    private final String openstackImageName;
    @NotNull
    private final String flavorName;
    @NotNull
    private final boolean autoFloatingIp;
    @NotNull
    private final CreateServerOptions options;
    @Nullable
    private final String userScriptPath;
    @NotNull
    private final ServerPaths serverPaths;
    @NotNull
    private final ScheduledExecutorService executor;

    @NotNull
    private final Map<String, OpenstackCloudInstance> instances = new ConcurrentHashMap<>();
    @NotNull
    private final IdGenerator instanceIdGenerator = new IdGenerator();
    @Nullable
    private final CloudErrorInfo errorInfo;

    public OpenstackCloudImage(@NotNull final OpenstackApi openstackApi, @NotNull final String imageId, @NotNull final String imageName,
            @NotNull final String openstackImageName, @NotNull final String flavorId, @NotNull boolean autoFloatingIp,
            @NotNull final CreateServerOptions options, @Nullable final String userScriptPath, @NotNull final ServerPaths serverPaths,
            @NotNull final ScheduledExecutorService executor) {
        this.openstackApi = openstackApi;
        this.imageId = imageId;
        this.imageName = imageName;
        this.openstackImageName = openstackImageName;
        this.flavorName = flavorId;
        this.autoFloatingIp = autoFloatingIp;
        this.options = options;
        this.userScriptPath = userScriptPath;
        this.serverPaths = serverPaths;
        this.executor = executor;

        this.errorInfo = null; // FIXME: need to use this, really.

        this.executor.scheduleWithFixedDelay(new VerboseRunnable(() -> {
            for (OpenstackCloudInstance instance : getInstances()) {
                instance.updateStatus();
                if (instance.getStatus() == InstanceStatus.STOPPED || instance.getStatus() == InstanceStatus.ERROR) {
                    forgetInstance(instance);
                }
            }
        }, true), 3, 3, TimeUnit.SECONDS);
    }

    @NotNull
    public ServerApi getNovaServerApi() {
        return openstackApi.getNovaServerApi();
    }

    public String getFloatingIpAvailable() {
        return openstackApi.getFloatingIpAvailable();
    }

    public void associateFloatingIp(String serverId, String ip) {
        openstackApi.associateFloatingIp(serverId, ip);
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

    @NotNull
    public boolean isAutoFloatingIp() {
        return this.autoFloatingIp;
    }

    @Nullable
    public String getUserScriptPath() {
        return this.userScriptPath;
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
