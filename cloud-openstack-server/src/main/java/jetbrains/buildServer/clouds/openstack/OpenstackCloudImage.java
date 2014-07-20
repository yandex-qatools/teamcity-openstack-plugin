package jetbrains.buildServer.clouds.openstack;

import jetbrains.buildServer.clouds.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

public class OpenstackCloudImage implements CloudImage {
    @NotNull private final String imageId;
    @NotNull private final String imageName;
    @NotNull private final String openstackImageName;
    @NotNull private final String hardwareName;
    @NotNull private final String securityGroupName;
    @NotNull private final String keyPair;
    @NotNull private final String zone;
    @NotNull private final String networkName;
    @NotNull private final File agentHomeDir;

    @NotNull private final Map<String, OpenstackCloudInstance> instances = new ConcurrentHashMap<String, jetbrains.buildServer.clouds.openstack.OpenstackCloudInstance>();
    @NotNull private final IdGenerator myInstanceIdGenerator = new IdGenerator();
    @Nullable private final CloudErrorInfo errorInfo;

    private boolean myIsReusable;
    private final Map<String, String> myExtraProperties = new HashMap<String, String>();

    @NotNull private final ScheduledExecutorService myExecutor;

    public OpenstackCloudImage(@NotNull final String imageId,
                           @NotNull final String imageName,
                           @NotNull final String agentHomePath,
                           @NotNull final String openstackImageName,
                           @NotNull final String hardwareName,
                           @NotNull final String securityGroupName,
                           @NotNull final String keyPair,
                           @NotNull final String zone,
                           @NotNull final String networkName,
                           @NotNull final ScheduledExecutorService executor) {
        this.imageId = imageId;
        this.imageName = imageName;
        this.agentHomeDir = new File(agentHomePath);
        this.openstackImageName = openstackImageName;
        this.hardwareName = hardwareName;
        this.securityGroupName = securityGroupName;
        this.keyPair = keyPair;
        this.zone = zone;
        this.networkName = networkName;
        this.myExecutor = executor;

        this.errorInfo = agentHomeDir.isDirectory() ? null : new CloudErrorInfo("\"" + agentHomePath + "\" is not a directory or does not exist.");
    }

    public boolean isReusable() {
        return myIsReusable;
    }

    // TODO: enable this as optional image paramter
    public void setIsReusable(boolean isReusable) {
        myIsReusable = isReusable;
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
    public File getAgentHomeDir() {
        return agentHomeDir;
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
        for (Map.Entry<String, String> e : myExtraProperties.entrySet()) {
            data.addAgentConfigurationParameter(e.getKey(), e.getValue());
        }

        //check reusable instances
        for (OpenstackCloudInstance instance : instances.values()) {
            if (instance.getErrorInfo() == null && instance.getStatus() == InstanceStatus.STOPPED && instance.isRestartable()) {
                instance.start(data);
                return instance;
            }
        }

        final String instanceId = myInstanceIdGenerator.next();
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
