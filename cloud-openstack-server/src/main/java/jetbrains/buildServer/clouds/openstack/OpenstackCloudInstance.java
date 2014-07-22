package jetbrains.buildServer.clouds.openstack;

import com.google.common.collect.Iterables;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.WaitFor;
import org.apache.log4j.Logger;
import org.jclouds.compute.domain.NodeMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static jetbrains.buildServer.clouds.openstack.OpenstackCloudParameters.IMAGE_ID_PARAM_NAME;
import static jetbrains.buildServer.clouds.openstack.OpenstackCloudParameters.INSTANCE_ID_PARAM_NAME;


public abstract class OpenstackCloudInstance implements CloudInstance {
    @NotNull private static final Logger LOG = Logger.getLogger(OpenstackCloudInstance.class);
    private static final int STATUS_WAITING_TIMEOUT = 30 * 1000;

    @NotNull private final String instanceId;
    @NotNull private final OpenstackCloudImage cloudImage;
    @NotNull private final Date myStartDate;

    private final AtomicReference<InstanceStatus> status = new AtomicReference<InstanceStatus>(InstanceStatus.SCHEDULED_TO_START);
    @Nullable private volatile CloudErrorInfo errorInfo;

    @Nullable private NodeMetadata nodeMetadata;

    @NotNull private final ScheduledExecutorService myAsync;

    public OpenstackCloudInstance(@NotNull final OpenstackCloudImage image, @NotNull final String instanceId, @NotNull ScheduledExecutorService executor) {
        this.cloudImage = image;
        this.instanceId = instanceId;
        this.myStartDate = new Date();
        this.myAsync = executor;

        setStatus(InstanceStatus.SCHEDULED_TO_START);
    }

    public abstract boolean isRestartable();

    @NotNull
    public InstanceStatus getStatus() {
        final CloudErrorInfo er = getErrorInfo();
        return er != null ? InstanceStatus.ERROR : status.get();
    }

    public void setStatus(@NotNull InstanceStatus status) {
        this.status.set(status);
    }

    @NotNull
    public String getInstanceId() {
        return instanceId;
    }

    @NotNull
    public String getName() {
        return OpenstackCloudClient.generateAgentName(cloudImage, instanceId) + " (" + cloudImage.getOpenstackImageName() + ":" + cloudImage.getOpenstackFalvorName() + ")";
    }

    @NotNull
    public String getImageId() {
        return cloudImage.getId();
    }

    @NotNull
    public OpenstackCloudImage getImage() {
        return cloudImage;
    }

    @NotNull
    public Date getStartedTime() {
        return myStartDate;
    }

    public String getNetworkIdentity() {
        return "clouds.openstack." + getImageId() + "." + instanceId;
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    public boolean containsAgent(@NotNull final AgentDescription agentDescription) {
        final Map<String, String> configParams = agentDescription.getConfigurationParameters();
        return instanceId.equals(configParams.get(INSTANCE_ID_PARAM_NAME)) &&
                getImageId().equals(configParams.get(IMAGE_ID_PARAM_NAME));
    }

    public void start(@NotNull final CloudInstanceUserData data) {
        setStatus(InstanceStatus.STARTING);
        myAsync.submit(ExceptionUtil.catchAll("start openstack cloud: " + this, new StartAgentCommand(data)));
    }

    public void restart() {
        waitForStatus(InstanceStatus.RUNNING);
        setStatus(InstanceStatus.RESTARTING);
        try {
            //doStop();
            Thread.sleep(3000);
            //doStart();
        } catch (final Exception e) {
            processError(e);
        }
    }

    public void terminate() {
        setStatus(InstanceStatus.STOPPING);
        try {
            //doStop();
            setStatus(InstanceStatus.STOPPED);
            cleanupStoppedInstance();
        } catch (final Exception e) {
            processError(e);
        }
    }

    protected abstract void cleanupStoppedInstance();

    private void waitForStatus(@NotNull final InstanceStatus status) {
        new WaitFor(STATUS_WAITING_TIMEOUT) {
            @Override
            protected boolean condition() {
                return status == status;
            }
        };
    }

    private void processError(@NotNull final Exception e) {
        final String message = e.getMessage();
        LOG.error(message, e);
        errorInfo = new CloudErrorInfo(message, message, e);
        setStatus(InstanceStatus.ERROR);
    }

    private class StartAgentCommand implements Runnable {
        private final CloudInstanceUserData data;

        public StartAgentCommand(@NotNull final CloudInstanceUserData data) {
            this.data = data;
        }

//        private void updateAgentProperties(@NotNull final CloudInstanceUserData data) throws IOException {
//            File inConfigFile = new File(new File(myBaseDir, "conf"), "buildAgent.properties"), outConfigFile = inConfigFile;
//            if (!inConfigFile.isFile()) {
//                inConfigFile = new File(new File(myBaseDir, "conf"), "buildAgent.dist.properties");
//                if (!inConfigFile.isFile()) {
//                    inConfigFile = null;
//                }
//            }
//            final Properties config = PropertiesUtil.loadProperties(inConfigFile);
//
//            config.put("serverUrl", data.getServerAddress());
//            config.put("workDir", "../work");
//            config.put("tempDir", "../temp");
//            config.put("systemDir", "../system");
//
//            //agent name and auth-token must be patched only once
//            if (!myIsConfigPatched.getAndSet(true)) {
//                config.put("name", data.getAgentName());
//                config.put("authorizationToken", data.getAuthToken());
//            }
//            for (final Map.Entry<String, String> param : data.getCustomAgentConfigurationParameters().entrySet()) {
//                config.put(param.getKey(), param.getValue());
//            }
//            config.put(IMAGE_ID_PARAM_NAME, getImageId());
//            config.put(INSTANCE_ID_PARAM_NAME, instanceId);
//            PropertiesUtil.storeProperties(config, outConfigFile, null);
//        }

        public void run() {
            try {
                NodeMetadata nodeMetaData = Iterables.getOnlyElement(cloudImage.getComputeService().createNodesInGroup(cloudImage.getName(), 1, cloudImage.getTemplate()));
                //updateAgentProperties(data);
                setStatus(InstanceStatus.RUNNING);
            } catch (final Exception e) {
                processError(e);
            }
        }
    }
}
