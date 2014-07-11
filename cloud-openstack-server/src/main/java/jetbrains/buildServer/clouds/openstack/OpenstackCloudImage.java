package jetbrains.buildServer.clouds.openstack;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

public class OpenstackCloudImage implements CloudImage {

    @NotNull
    private final String id;
    @NotNull
    private final String name;
    @NotNull
    private final String hardwareName;
    @NotNull
    private final String securityGroupName;
    @NotNull
    private final String keyPair;
    @Nullable
    private final String zone;
    @Nullable
    private final String networkName;
    @Nullable
    private final CloudErrorInfo errorInfo;
    @NotNull
    private final Map<String, OpenstackCloudInstance> myInstances = new ConcurrentHashMap<String, OpenstackCloudInstance>();
    @NotNull
    private final IdGenerator myInstanceIdGenerator = new IdGenerator();
    private final Map<String, String> myExtraProperties = new HashMap<String, String>();
    @NotNull
    private final ScheduledExecutorService myExecutor;
    private boolean myIsReusable;
    private boolean myIsEternalStarting;

    public OpenstackCloudImage(
            @NotNull final String id,
            @NotNull final String name,
            @NotNull final String hardwareName,
            @NotNull final String securityGroupName,
            @NotNull final String agentHomePath,
            @NotNull final String keyPair,
            @NotNull final String zone,
            @NotNull final String networkName,
            @NotNull final ScheduledExecutorService executor) {
        this.id = id;
        this.name = name;
        this.hardwareName = hardwareName;
        this.securityGroupName = securityGroupName;
        this.keyPair = keyPair;
        this.zone = zone;
        this.networkName = networkName;
        this.myExecutor = executor;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getId() {
        return id;
    }

    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    public void setIsReusable(boolean isReusable) {
        myIsReusable = isReusable;
    }

    public boolean isEternalStarting() {
        return myIsEternalStarting;
    }

    public void setIsEternalStarting(boolean isEternalStarting) {
        myIsEternalStarting = isEternalStarting;
    }

    public void addExtraProperty(@NotNull final String name, @NotNull final String value) {
        myExtraProperties.put(name, value);
    }

    @NotNull
    public Map<String, String> getExtraProperties() {
        return myExtraProperties;
    }

    @NotNull
    public Collection<? extends CloudInstance> getInstances() {
        return Collections.unmodifiableCollection(myInstances.values());
    }

    @Nullable
    public OpenstackCloudInstance findInstanceById(@NotNull final String instanceId) {
        return myInstances.get(instanceId);
    }

    @NotNull
    public synchronized OpenstackCloudInstance startNewInstance(@NotNull final CloudInstanceUserData data) {
        for (Map.Entry<String, String> e : myExtraProperties.entrySet()) {
            data.addAgentConfigurationParameter(e.getKey(), e.getValue());
        }

        //check reusable instances
//    for (OpenstackCloudInstance instance : myInstances.values()) {
//      if (instance.getErrorInfo() == null && instance.getStatus() == InstanceStatus.STOPPED && instance.isRestartable()) {
//        instance.start(data);
//        return instance;
//      }
//    }

        final String instanceId = myInstanceIdGenerator.next();
        final OpenstackCloudInstance instance = createInstance(instanceId);
        myInstances.put(instanceId, instance);
        instance.start(data);
        return instance;
    }

    protected OpenstackCloudInstance createInstance(String instanceId) {
//        if (isReusable()) {
//            return new ReStartableInstance(instanceId, this, myExecutor);
//        }
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
