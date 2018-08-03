package jetbrains.buildServer.clouds.openstack;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    @Nullable
    private final String volumeName;
    @Nullable
    private final String volumeDevice;
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

    public OpenstackCloudImage(@NotNull final CreateImageOptions cio) {
        this.openstackApi = cio.getOpenstackApi();
        this.imageId = cio.getImageId();
        this.imageName = cio.getImageName();
        this.openstackImageName = cio.getOpenstackImageName();
        this.flavorName = cio.getFlavorName();
        this.volumeName = cio.getVolumeName();
        this.volumeDevice = cio.getVolumeDevice();
        this.autoFloatingIp = cio.isAutoFloatingIp();
        this.userScriptPath = cio.getUserScriptPath();
        this.serverPaths = cio.getServerPaths();
        this.options = cio.getCreateServerOptions();
        this.executor = cio.getScheduledExecutorService();

        this.errorInfo = null; // FIXME: need to use this, really.

        this.executor.scheduleWithFixedDelay(new VerboseRunnable(() -> {
            for (OpenstackCloudInstance instance : getInstances()) {
                instance.updateStatus();
                if (instance.getStatus() == InstanceStatus.STOPPED || instance.getStatus() == InstanceStatus.ERROR)
                    forgetInstance(instance);
            }
        }, true), 3, 3, TimeUnit.SECONDS);
    }

    private void forgetInstance(@NotNull final OpenstackCloudInstance instance) {
        instances.remove(instance.getInstanceId());
    }

    @Nullable
    public OpenstackCloudInstance findInstanceById(@NotNull final String instanceId) {
        return instances.get(instanceId);
    }

    @NotNull
    public Collection<? extends OpenstackCloudInstance> getInstances() {
        return Collections.unmodifiableCollection(instances.values());
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
        final OpenstackCloudInstance instance = new OpenstackCloudInstance(this, instanceId, openstackApi, serverPaths, executor);

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

    @Nullable
    public String getVolumeName() {
        return this.volumeName;
    }

    @Nullable
    public String getVolumeDevice() {
        return this.volumeDevice;
    }
}
