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
    private final String openstackImageName;
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
    private boolean myIsReusable;
    @NotNull
    private final Map<String, OpenstackCloudInstance> myInstances = new ConcurrentHashMap<String, OpenstackCloudInstance>();
    @NotNull
    private final IdGenerator myInstanceIdGenerator = new IdGenerator();
    private final Map<String, String> myExtraProperties = new HashMap<String, String>();
    @NotNull
    private final ScheduledExecutorService myExecutor;

    public OpenstackCloudImage(
            @NotNull final String id,
            @NotNull final String name,
            @NotNull final String openstackImageName,
            @NotNull final String hardwareName,
            @NotNull final String securityGroupName,
            @NotNull final String keyPair,
            @NotNull final String zone,
            @NotNull final String networkName,
            @NotNull final ScheduledExecutorService executor) {
        this.id = id;
        this.name = name;
        this.openstackImageName = openstackImageName;
        this.hardwareName = hardwareName;
        this.securityGroupName = securityGroupName;
        this.keyPair = keyPair;
        this.zone = zone;
        this.networkName = networkName;
        this.myExecutor = executor;
        this.errorInfo = null; // FIXME

        System.out.println("image initialized");
    }

    public boolean isReusable() {
        return myIsReusable;
    }

    public void setIsReusable(boolean isReusable) {
        myIsReusable = isReusable;
    }

    @NotNull
    public String getName() {
        System.out.println("getName");
        return name;
    }

    @NotNull
    public String getId() {
        System.out.println("getIdImage: " + id);
        return id;
    }

    public CloudErrorInfo getErrorInfo() {
        System.out.println("getErrorInfoImage: " + errorInfo);
        return errorInfo;
    }

    @NotNull
    public Collection<? extends CloudInstance> getInstances() {
        System.out.println("getInstances: " + myInstances.values());
        return Collections.unmodifiableCollection(myInstances.values());
    }

    @Nullable
    public OpenstackCloudInstance findInstanceById(@NotNull final String instanceId) {
        System.out.println("findInstanceById");
        return myInstances.get(instanceId);
    }

    @NotNull
    public synchronized OpenstackCloudInstance startNewInstance(@NotNull final CloudInstanceUserData data) {
        System.out.println("startNewInstance");
        for (Map.Entry<String, String> e : myExtraProperties.entrySet()) {
            data.addAgentConfigurationParameter(e.getKey(), e.getValue());
        }

        final String instanceId = myInstanceIdGenerator.next();
        final OpenstackCloudInstance instance = createInstance(instanceId);
        myInstances.put(instanceId, instance);
        instance.start(data);
        return instance;
    }

    protected OpenstackCloudInstance createInstance(String instanceId) {
        System.out.println("createInstance");
//        if (isReusable()) {
//            return new ReStartableInstance(instanceId, this, myExecutor);
//        }
        return new OneUseOpenstackCloudInstance(instanceId, this, myExecutor);
    }

    void forgetInstance(@NotNull final OpenstackCloudInstance instance) {
        System.out.println("forgetInstance");
        myInstances.remove(instance.getInstanceId());
    }

    void dispose() {
        System.out.println("dispose");
        for (final OpenstackCloudInstance instance : myInstances.values()) {
            instance.terminate();
        }
        myInstances.clear();
    }
}
