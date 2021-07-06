package jetbrains.buildServer.clouds.openstack;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import jetbrains.buildServer.serverSide.TeamCityProperties;

public class OpenstackCloudImage implements CloudImage {

    @NotNull
    public static final String DELAY_RESTORE_DELAY_KEY = "openstack.restore.delay";
    @NotNull
    public static final int DELAY_RESTORE_DELAY_DEFAULT_VALUE = 1;

    @NotNull
    public static final String DELAY_STATUS_INITIAL_KEY = "openstack.status.initial";
    @NotNull
    public static final int DELAY_STATUS_INITIAL_DEFAULT_VALUE = 5;

    @NotNull
    public static final String DELAY_STATUS_DELAY_KEY = "openstack.status.delay";
    @NotNull
    public static final int DELAY_STATUS_DELAY_DEFAULT_VALUE = 10;

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
    private CloudErrorInfo errorInfo = null;

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
        this.executor.scheduleWithFixedDelay(new VerboseRunnable(() -> {
            // Update status of instances managed by this image
            LOG.debug(String.format("Updating instances status for openstack image: %s", getName()));
            Map<String, Server.Status> status = new HashMap<>();
            try {
                for (Server server : openstackApi.getNovaServerApi().listInDetail().concat().filter(p -> p.getName().startsWith(getName()))) {
                    status.put(server.getName(), server.getStatus());
                }
                resetAnyPreviousError();
            } catch (Exception e) {
                // All current instances will be set in error
                processError("Instances status cannot be updated", e);
            }
            for (OpenstackCloudInstance instance : getInstances()) {
                // If any error on global status retrieve, fill UNKNOW, avoiding any occasional (and not wanted) termination
                instance.updateStatus(getErrorInfo() != null ? Server.Status.UNKNOWN : status.get(instance.getName()));
                if (instance.getStatus() == InstanceStatus.STOPPED || instance.getStatus() == InstanceStatus.ERROR) {
                    forgetInstance(instance);
                }
            }
        }, true), getTeamCityProperty(DELAY_STATUS_INITIAL_KEY, DELAY_STATUS_INITIAL_DEFAULT_VALUE),
                getTeamCityProperty(DELAY_STATUS_DELAY_KEY, DELAY_STATUS_DELAY_DEFAULT_VALUE), TimeUnit.SECONDS);
    }

    private void processError(@NotNull String process, @NotNull final Exception e) {
        final String message = e.getMessage();
        LOG.error(message, e);
        errorInfo = new CloudErrorInfo(process, message, e);
    }

    private void resetAnyPreviousError() {
        errorInfo = null;
    }

    // Initialize the image
    void initialize() {
        final String openstackImageId = initialGetOpenstackImageId(5);
        if (openstackImageId != null && !openstackImageId.isEmpty()) {
            this.executor.schedule(new VerboseRunnable(() -> restoreInstances(openstackImageId), true),
                    getTeamCityProperty(DELAY_RESTORE_DELAY_KEY, DELAY_RESTORE_DELAY_DEFAULT_VALUE), TimeUnit.SECONDS);
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
    private void restoreInstances(String openstackImageId) {
        try {
            LOG.info(String.format("Restore potential instances for openstack image: %s", getName()));
            for (Server server : openstackApi.getNovaServerApi().listInDetail().concat().filter(p -> p.getName().startsWith(getName()))) {
                // Restore servers of the specified image id (all status, some could be shutdown but not terminated)
                Resource simage = server.getImage();
                if (simage != null && openstackImageId.equals(simage.getId())) {
                    final String instanceId = server.getName().substring(server.getName().lastIndexOf('-') + 1);
                    if (!instances.containsKey(instanceId)) {
                        // Add only if not already existing (sample: started at profile creation)
                        final OpenstackCloudInstance instance = new OpenstackCloudInstance(this, instanceId, serverPaths, executor, server);
                        instances.put(instanceId, instance);
                    }
                }
            }
            resetAnyPreviousError();
        } catch (Exception e) {
            processError("Current instances (if any) cannot be restored", e);
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
        // Image are affected to 'default' agents pool at creation (required for TeamCity v2021.1 / TW-71939)
        // Global "Agents Pools" TeamCity feature should be used to affect image(s) to some pool if needed
        return 0;
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    @NotNull
    synchronized String getNextInstanceId() {
        return instanceIdGenerator.next();
    }

    @NotNull
    public synchronized OpenstackCloudInstance startNewInstance(@NotNull final CloudInstanceUserData data) {
        final String instanceId = getNextInstanceId();
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

    private int getTeamCityProperty(String key, int defaultValue) {
        return TeamCityProperties.getInteger(key, defaultValue);
    }

}
