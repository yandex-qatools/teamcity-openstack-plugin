package jetbrains.buildServer.clouds.openstack;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jclouds.openstack.v2_0.domain.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.diagnostic.Logger;
import com.jcabi.log.VerboseRunnable;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.ServerPaths;

public class OpenstackCloudImage implements CloudImage {
    @NotNull
    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT);
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

    // Initialize the image
    public void initialize() {
        final String openstackImageId = initialGetOpenstackImageId(5);
        if (openstackImageId != null && !openstackImageId.isEmpty()) {
            this.executor.schedule(new VerboseRunnable(() -> restoreInstances(openstackImageId), true), 1, TimeUnit.SECONDS);
        }
    }

    // Initially obtain openstack image id
    private String initialGetOpenstackImageId(int trials) {
        for (int i = 0; i < trials; i++) {
            String v = openstackApi.getImageIdByName(openstackImageName);
            if (v != null && !v.isEmpty())
                return v;
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    // Restore instances of the image
    public void restoreInstances(String openstackImageId) {
        LOG.info(String.format("Restore potential instances for openstack image: %s", imageName));
        Collection<Server> list = openstackApi.getNovaServerApi().listInDetail().concat().toList();
        for (Server server : list) {
            // Restore servers of the specified image id, only with ACTIVE status
            Resource simage = server.getImage();
            if (simage != null && openstackImageId.equals(simage.getId()) && Server.Status.ACTIVE.equals(server.getStatus())) {
                final String instanceId = server.getName().substring(server.getName().lastIndexOf('-') + 1);
                if (!instances.containsKey(instanceId)) {
                    // Add only if not already existing (sample: started at profile creation)
                    final OpenstackCloudInstance instance = new OpenstackCloudInstance(this, instanceId, serverPaths, executor, server);
                    instances.put(instanceId, instance);
                }
            }
        }
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
        LOG.debug(String.format("findInstanceById(%s)", instanceId));
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
        LOG.debug(String.format("Dispose image %s (id=%s)", imageName, imageId));
        instances.clear();
        executor.shutdown();
    }
}
