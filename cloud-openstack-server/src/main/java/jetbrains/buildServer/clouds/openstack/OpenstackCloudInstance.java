package jetbrains.buildServer.clouds.openstack;

import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityServerProperties;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.FileUtil;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
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
        LOG.debug(String.format("Pinging %s for status", getName()));
        if (serverCreated != null) {
            Server server = cloudImage.getNovaApi().get(serverCreated.getId());
            if (server != null) {
                Server.Status currentStatus = server.getStatus();
                LOG.debug(String.format("Getting instance status from openstack for %s, result is %s", getName(), currentStatus));
                switch(currentStatus) {
                    case ACTIVE:
                        setStatus(InstanceStatus.RUNNING);
                        break;
                    case ERROR:
                        setStatus(InstanceStatus.ERROR);
                        terminate();
                        break;
                    case SUSPENDED:
                    case PAUSED:
                    case DELETED:
                    case SOFT_DELETED:
                    case UNKNOWN:
                    case UNRECOGNIZED:
                    case SHUTOFF:
                    case SHELVED:
                    case SHELVED_OFFLOADED:
                    case STOPPED:
                        setStatus(InstanceStatus.STOPPED);
                        break;
                    default:
                        break;
                }
            } else {
                setStatus(InstanceStatus.STOPPED);
            }
        } else {
            LOG.debug("Will skip status updating cause instance is not created yet");
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

    private Map<String, String> getMetadata(@NotNull final CloudInstanceUserData data) {
        Map<String, String> metadata = new HashMap<>();
        metadata.putAll(data.getCustomAgentConfigurationParameters());
        metadata.put(OpenstackCloudParameters.SERVER_URL, data.getServerAddress());
        return metadata;
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
                options.metadata(getMetadata(userData));

                // TODO: that code should be in OpenstackCloudImage
                // but as we make it possible to change userScript
                // without touching teamcity, that hack takes place
                // sorry
                String userScriptPath = cloudImage.getUserScriptPath();
                if (!Strings.isNullOrEmpty(userScriptPath)) {
                    String teamcityPath = TeamCityServerProperties.getDataPath();
                    File pluginData = new ServerPaths(teamcityPath).getPluginDataDirectory();
                    File userScriptFile = new File(new File(pluginData, OpenstackCloudParameters.PLUGIN_SHORT_NAME), userScriptPath);
                    try {
                        String userScript = FileUtil.readText(userScriptFile);
                        options.userData(userScript.trim().getBytes(StandardCharsets.UTF_8)) // this is userScript actually, but CreateServerOptions calls it userData
                                .configDrive(true);
                    } catch (IOException e) {
                        LOG.error(e.getMessage());
                    }
                }

                LOG.debug(String.format("Creating openstack instance with flavorId=%s, imageId=%s, options=%s", flavorId, openstackImageId, options));
                serverCreated = cloudImage.getNovaApi().create(getName(), openstackImageId, flavorId, options);

                setStatus(InstanceStatus.STARTING);
            } catch (final Exception e) {
                processError(e);
            }
        }
    }
}
