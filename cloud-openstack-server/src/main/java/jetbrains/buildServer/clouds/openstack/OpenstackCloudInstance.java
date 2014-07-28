package jetbrains.buildServer.clouds.openstack;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.WaitFor;
import org.apache.log4j.Logger;
import org.jclouds.openstack.nova.v2_0.domain.RebootType;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;


public class OpenstackCloudInstance implements CloudInstance {
    @NotNull private static final Logger LOG = Logger.getLogger(OpenstackCloudInstance.class);
    private static final int STATUS_WAITING_TIMEOUT = 30 * 1000;

    @NotNull private final String instanceId;
    @NotNull private final OpenstackCloudImage cloudImage;
    @NotNull private final Date startDate;
    @Nullable private volatile CloudErrorInfo errorInfo;
    @Nullable private ServerCreated serverCreated;
    @NotNull private final ScheduledExecutorService executor;

    private final AtomicReference<InstanceStatus> status = new AtomicReference<InstanceStatus>(InstanceStatus.SCHEDULED_TO_START);

    public OpenstackCloudInstance(@NotNull final OpenstackCloudImage image, @NotNull final String instanceId, @NotNull ScheduledExecutorService executor) {
        this.cloudImage = image;
        this.instanceId = instanceId;
        this.startDate = new Date();
        this.executor = executor;
        setStatus(InstanceStatus.SCHEDULED_TO_START);
    }

    public synchronized void updateStatus() {
        if (serverCreated != null) {
            Server server = cloudImage.getNovaApi().get(serverCreated.getId());
            if (server != null) {
                Server.Status currentStatus = server.getStatus();
                if (currentStatus == Server.Status.ACTIVE) setStatus(InstanceStatus.RUNNING);
                if (currentStatus == Server.Status.DELETED || currentStatus == Server.Status.SOFT_DELETED) setStatus(InstanceStatus.STOPPED);
            } else {
                setStatus(InstanceStatus.STOPPED);
            }
        }
    }

    @Nullable
    public String getOpenstackInstanceId() {
        return serverCreated != null ? serverCreated.getId() : "";
    }

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
        return cloudImage.getName() + "-" + instanceId;
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
        return startDate;
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
        return configParams.containsValue(OpenstackCloudParameters.CLOUD_TYPE) &&
                getOpenstackInstanceId().equals(configParams.get(OpenstackCloudParameters.OPENSTACK_INSTANCE_ID));
    }

    public void start(@NotNull final CloudInstanceUserData data) {
        data.setAgentRemovePolicy(CloudConstants.AgentRemovePolicyValue.RemoveAgent);
        setStatus(InstanceStatus.STARTING);
        executor.submit(ExceptionUtil.catchAll("start openstack cloud: " + this, new StartAgentCommand(data)));
    }

    public void restart() {
        waitForStatus(InstanceStatus.RUNNING);
        setStatus(InstanceStatus.RESTARTING);
        try {
            if (serverCreated != null) {
                cloudImage.getNovaApi().reboot(serverCreated.getId(), RebootType.SOFT);
                setStatus(InstanceStatus.RUNNING);
            }
        } catch (final Exception e) {
            processError(e);
        }
    }

    public void terminate() {
        setStatus(InstanceStatus.SCHEDULED_TO_STOP);
        try {
            if (serverCreated != null) {
                cloudImage.getNovaApi().delete(serverCreated.getId());
                setStatus(InstanceStatus.STOPPING);
            }
        } catch (final Exception e) {
            processError(e);
        }
    }

    private void waitForStatus(@NotNull final InstanceStatus st) {
        new WaitFor(STATUS_WAITING_TIMEOUT) {
            @Override
            protected boolean condition() {
                return status.equals(st);
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
        public StartAgentCommand(@NotNull final CloudInstanceUserData data) {}

        public void run() {
            try {
                String openstackImageId = cloudImage.getOpenstackImageId();
                String flavorId = cloudImage.getFlavorId();
                CreateServerOptions options = cloudImage.getImageOptions();

                serverCreated = cloudImage.getNovaApi().create(getName(), openstackImageId, flavorId, options);

                setStatus(InstanceStatus.STARTING);
            } catch (final Exception e) {
                processError(e);
            }
        }
    }
}
