package jetbrains.buildServer.clouds.openstack;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.util.ExceptionUtil;
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
    @NotNull private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT);
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
                LOG.debug(String.format("Getting instance status from openstack for %s, result is %s", getName(), currentStatus));
                switch(currentStatus) {
                    case ACTIVE:
                        setStatus(InstanceStatus.RUNNING);
                        break;
                    case DELETED:
                        setStatus(InstanceStatus.STOPPED);
                        break;
                    case SOFT_DELETED:
                        setStatus(InstanceStatus.STOPPED);
                        break;
                    case ERROR:
                        setStatus(InstanceStatus.ERROR);
                        terminate();
                        break;
                }
            } else {
                setStatus(InstanceStatus.STOPPED);
            }
        } else {
            LOG.debug(String.format("Will skip status updating cause instance is not created yet"));
        }
    }

    @NotNull
    public String getOpenstackInstanceId() {
        return serverCreated != null ? serverCreated.getId() : "";
    }

    @NotNull
    public InstanceStatus getStatus() {
        final CloudErrorInfo er = getErrorInfo();
        return er != null ? InstanceStatus.ERROR : status.get();
    }

    public void setStatus(@NotNull InstanceStatus status) {
        LOG.debug(String.format("Changing %s status from %s to %s ", getName(), this.status, status));
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
        LOG.info(String.format("Starting cloud openstack instance %s", getName()));
        data.setAgentRemovePolicy(CloudConstants.AgentRemovePolicyValue.RemoveAgent);
        executor.submit(ExceptionUtil.catchAll("start openstack cloud: " + this, new StartAgentCommand(data)));
    }

    public void restart() {
        throw new UnsupportedOperationException("Restart openstack instance operation is not supported yet" );
    }

    public void terminate() {
        LOG.info(String.format("Terminating cloud openstack instance %s", getName()));
        setStatus(InstanceStatus.SCHEDULED_TO_STOP);
        try {
            if (serverCreated != null) {
                cloudImage.getNovaApi().stop(serverCreated.getId());
                cloudImage.getNovaApi().delete(serverCreated.getId());
                setStatus(InstanceStatus.STOPPING);
            }
        } catch (final Exception e) {
            processError(e);
        }
    }

    private void processError(@NotNull final Exception e) {
        final String message = e.getMessage();
        LOG.error(message, e);
        errorInfo = new CloudErrorInfo(message, message, e);
        setStatus(InstanceStatus.ERROR);
    }

    private class StartAgentCommand implements Runnable {
        private final CloudInstanceUserData userData;
        public StartAgentCommand(@NotNull final CloudInstanceUserData data) {
            this.userData = data;
        }

        public void run() {
            try {
                String openstackImageId = cloudImage.getOpenstackImageId();
                String flavorId = cloudImage.getFlavorId();
                CreateServerOptions options = cloudImage.getImageOptions();
                options.userData(userData.serialize().getBytes());

                LOG.debug(String.format("Creating openstack instance with flavorId=%s, imageId=%s, options=%s", flavorId, openstackImageId, options));
                serverCreated = cloudImage.getNovaApi().create(getName(), openstackImageId, flavorId, options);

                setStatus(InstanceStatus.STARTING);
            } catch (final Exception e) {
                processError(e);
            }
        }
    }
}
