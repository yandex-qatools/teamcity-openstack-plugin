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
    @NotNull private final String myId;
    @NotNull private final String myName;
    @NotNull private final File myAgentHomeDir;
    @NotNull private final Map<String, OpenstackCloudInstance> myInstances = new ConcurrentHashMap<String, jetbrains.buildServer.clouds.openstack.OpenstackCloudInstance>();
    @NotNull private final IdGenerator myInstanceIdGenerator = new IdGenerator();
    @Nullable private final CloudErrorInfo myErrorInfo;
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
        myId = imageId;
        myName = imageName;
        myAgentHomeDir = new File(agentHomePath);
        myExecutor = executor;
        myErrorInfo = myAgentHomeDir.isDirectory() ? null : new CloudErrorInfo("\"" + agentHomePath + "\" is not a directory or does not exist.");
    }

    public boolean isReusable() {
        return myIsReusable;
    }

    public void setIsReusable(boolean isReusable) {
        myIsReusable = isReusable;
    }

    @NotNull
    public String getId() {
        return myId;
    }

    @NotNull
    public String getName() {
        return myName;
    }

    @NotNull
    public File getAgentHomeDir() {
        return myAgentHomeDir;
    }

    @NotNull
    public Collection<? extends CloudInstance> getInstances() {
        return Collections.unmodifiableCollection(myInstances.values());
    }

    @Nullable
    public OpenstackCloudInstance findInstanceById(@NotNull final String instanceId) {
        return myInstances.get(instanceId);
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        return myErrorInfo;
    }

    @NotNull
    public synchronized OpenstackCloudInstance startNewInstance(@NotNull final CloudInstanceUserData data) {
        for (Map.Entry<String, String> e : myExtraProperties.entrySet()) {
            data.addAgentConfigurationParameter(e.getKey(), e.getValue());
        }

        //check reusable instances
        for (OpenstackCloudInstance instance : myInstances.values()) {
            if (instance.getErrorInfo() == null && instance.getStatus() == InstanceStatus.STOPPED && instance.isRestartable()) {
                instance.start(data);
                return instance;
            }
        }

        final String instanceId = myInstanceIdGenerator.next();
        final OpenstackCloudInstance instance = createInstance(instanceId);
        myInstances.put(instanceId, instance);
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
        myInstances.remove(instance.getInstanceId());
    }

    void dispose() {
        for (final OpenstackCloudInstance instance : myInstances.values()) {
            instance.terminate();
        }
        myInstances.clear();
    }
}
