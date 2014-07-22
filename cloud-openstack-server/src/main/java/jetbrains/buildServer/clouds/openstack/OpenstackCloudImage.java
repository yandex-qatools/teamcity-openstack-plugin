package jetbrains.buildServer.clouds.openstack;

import jetbrains.buildServer.clouds.*;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Template;
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
    @NotNull private final ComputeService computeService;
    @NotNull private final Template template;

    @NotNull private final Map<String, OpenstackCloudInstance> instances = new ConcurrentHashMap<String, jetbrains.buildServer.clouds.openstack.OpenstackCloudInstance>();
    @NotNull private final IdGenerator instanceIdGenerator = new IdGenerator();
    @Nullable private final CloudErrorInfo errorInfo;
    private boolean myIsReusable;

    @NotNull private final ScheduledExecutorService myExecutor;

    public OpenstackCloudImage(@NotNull final String imageId,
                           @NotNull final String imageName,
                           @NotNull final ComputeService computeService,
                           @NotNull final Template template,
                           @NotNull final ScheduledExecutorService executor) {
        this.imageId = imageId;
        this.imageName = imageName;
        this.computeService = computeService;
        this.template = template;
        this.myExecutor = executor;

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
    public ComputeService getComputeService() {
        return computeService;
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
        return this.getTemplate().getImage().getName();
    }

    @NotNull
    public String getOpenstackFalvorName() {
        return this.getTemplate().getHardware().getName();
    }

    @NotNull
    public Template getTemplate() {
        return template;
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
            return new ReStartableInstance(instanceId, this, myExecutor);
        }
        return new OneUseOpenstackCloudInstance(instanceId, this, myExecutor);
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
