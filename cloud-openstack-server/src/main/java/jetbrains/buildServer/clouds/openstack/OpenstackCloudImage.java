package jetbrains.buildServer.clouds.openstack;

import jetbrains.buildServer.clouds.*;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

public class OpenstackCloudImage implements CloudImage {
    @NotNull private final String imageId;
    @NotNull private final String imageName;
    @NotNull private final String openstackImageId;
    @NotNull private final String flavorId;
    @NotNull private final OpenstackApi openstackApi;
    @NotNull private final CreateServerOptions options;
    @NotNull private final ScheduledExecutorService executor;

    @NotNull private final Map<String, OpenstackCloudInstance> instances = new ConcurrentHashMap<String, jetbrains.buildServer.clouds.openstack.OpenstackCloudInstance>();
    @NotNull private final IdGenerator instanceIdGenerator = new IdGenerator();
    @Nullable private final CloudErrorInfo errorInfo;
    private boolean myIsReusable;

    public OpenstackCloudImage(@NotNull final String imageId,
                           @NotNull final String imageName,
                           @NotNull final OpenstackApi openstackApi,
                           @NotNull final String openstackImageId,
                           @NotNull final String flavorId,
                           @NotNull final CreateServerOptions options,
                           @NotNull final ScheduledExecutorService executor) {
        this.imageId = imageId;
        this.imageName = imageName;
        this.openstackApi = openstackApi;
        this.openstackImageId = openstackImageId;
        this.flavorId = flavorId;
        this.options = options;
        this.executor = executor;

        this.errorInfo = null;  //FIXME
    }

    public boolean isReusable() {
        return myIsReusable;
    }

    // TODO: enable this as optional image paramter
    public void setIsReusable(boolean isReusable) {
        myIsReusable = isReusable;
    }

    @NotNull
    public ServerApi getNovaApi() {
        return openstackApi.getNovaApi();
    }

    @NotNull
    public CreateServerOptions getOptions() {
        return options;
    }

    @NotNull
    public String getOpenstackImageId() {
        return openstackApi.getImageIdByName(openstackImageId);
    }

    @NotNull
    public String getFlavorId() {
        return openstackApi.getFlavorIdByName(flavorId);
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
        return this.openstackImageId;
    }

    @NotNull
    public String getOpenstackFalvorName() {
        return this.flavorId;
    }

    @NotNull
    public Collection<? extends CloudInstance> getInstances() {
        return Collections.unmodifiableCollection(instances.values());
    }

    @Nullable
    public OpenstackCloudInstance findInstanceById(@NotNull final String instanceId) {
        return instances.get(instanceId);
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    @NotNull
    public synchronized OpenstackCloudInstance startNewInstance(@NotNull final CloudInstanceUserData data) {
        //check reusable instances
        for (OpenstackCloudInstance instance : instances.values()) {
            if (instance.getErrorInfo() == null && instance.getStatus() == InstanceStatus.STOPPED && instance.isRestartable()) {
                instance.start(data);
                return instance;
            }
        }

        final String instanceId = instanceIdGenerator.next();
        final OpenstackCloudInstance instance = createInstance(instanceId);
        instances.put(instanceId, instance);
        instance.start(data);
        return instance;
    }

    protected OpenstackCloudInstance createInstance(String instanceId) {
        if (isReusable()) {
            return new ReStartableInstance(instanceId, this, executor);
        }
        return new OneUseOpenstackCloudInstance(instanceId, this, executor);
    }

    void forgetInstance(@NotNull final OpenstackCloudInstance instance) {
        instances.remove(instance.getInstanceId());
    }

    void dispose() {
        for (final OpenstackCloudInstance instance : instances.values()) {
            instance.terminate();
        }
        instances.clear();
    }
}
