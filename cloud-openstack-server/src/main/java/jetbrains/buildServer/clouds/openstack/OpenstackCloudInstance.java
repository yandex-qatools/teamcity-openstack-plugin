package jetbrains.buildServer.clouds.openstack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

import jetbrains.buildServer.clouds.CloudConstants;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.FileUtil;

public class OpenstackCloudInstance implements CloudInstance {
    @NotNull
    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT);
    @NotNull
    private final String instanceId;
    @NotNull
    private final ServerPaths serverPaths;
    @NotNull
    private final OpenstackCloudImage cloudImage;
    @NotNull
    private final Date startDate;
    @Nullable
    private CloudErrorInfo errorInfo;
    @Nullable
    private ServerCreated serverCreated;
    @NotNull
    private final ScheduledExecutorService executor;
    private String ip;

    private final AtomicReference<InstanceStatus> status = new AtomicReference<>(InstanceStatus.SCHEDULED_TO_START);

    public OpenstackCloudInstance(@NotNull final OpenstackCloudImage image, @NotNull final String instanceId, @NotNull ServerPaths serverPaths,
            @NotNull ScheduledExecutorService executor) {
        this.cloudImage = image;
        this.instanceId = instanceId;
        this.serverPaths = serverPaths;
        this.startDate = new Date();
        this.executor = executor;
        setStatus(InstanceStatus.SCHEDULED_TO_START);
    }

    public synchronized void updateStatus() {
        LOG.debug(String.format("Pinging %s for status", getName()));
        if (serverCreated == null) {
            LOG.debug("Will skip status updating cause instance is not created yet");
            return;
        }
        try {
            Server server = cloudImage.getNovaServerApi().get(serverCreated.getId());
            if (server != null) {
                Server.Status currentStatus = server.getStatus();
                LOG.debug(String.format("Getting instance status from openstack for '%s', result is '%s' (previous internal status setted was '%s')",
                        getName(), currentStatus, getStatus()));
                switch (currentStatus) {
                case BUILD:
                case REBUILD:
                    setStatus(InstanceStatus.STARTING);
                    break;
                case ACTIVE:
                    // When OpenStack instance is stopping, the status is always 'ACTIVE' => check if termination started
                    if (InstanceStatus.SCHEDULED_TO_STOP.equals(getStatus()) || InstanceStatus.STOPPING.equals(getStatus())) {
                        setStatus(InstanceStatus.STOPPING);
                    } else {
                        setStatus(InstanceStatus.RUNNING);
                    }
                    break;
                case ERROR:
                    setStatus(InstanceStatus.ERROR);
                    terminate();
                    break;
                case SHUTOFF:
                    terminate();
                    break;
                case DELETED:
                case SUSPENDED:
                case PAUSED:
                case SOFT_DELETED:
                case UNKNOWN:
                case UNRECOGNIZED:
                case SHELVED:
                case SHELVED_OFFLOADED:
                default:
                    setStatus(InstanceStatus.STOPPED);
                    break;
                }
            } else {
                setStatus(InstanceStatus.STOPPED);
            }
        } catch (Exception e) {
            LOG.error("Got exception while calculating instance status, will terminate", e);
            setStatus(InstanceStatus.ERROR);
            terminate();
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
        return ip;
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    public boolean containsAgent(@NotNull final AgentDescription agentDescription) {
        final Map<String, String> configParams = agentDescription.getConfigurationParameters();
        return configParams.containsValue(OpenstackCloudParameters.CLOUD_TYPE)
                && getOpenstackInstanceId().equals(configParams.get(OpenstackCloudParameters.OPENSTACK_INSTANCE_ID));
    }

    public void start(@NotNull final CloudInstanceUserData data) {
        LOG.info(String.format("Starting cloud openstack instance %s", getName()));
        data.setAgentRemovePolicy(CloudConstants.AgentRemovePolicyValue.RemoveAgent);
        executor.submit(ExceptionUtil.catchAll("start openstack cloud: " + this, new StartAgentCommand(data)));
    }

    public void restart() {
        throw new UnsupportedOperationException("Restart openstack instance operation is not supported yet");
    }

    public void stop() {
        LOG.info(String.format("Stopping cloud openstack instance %s", getName()));
        setStatus(InstanceStatus.SCHEDULED_TO_STOP);
        try {
            if (serverCreated != null) {
                cloudImage.getNovaServerApi().stop(serverCreated.getId());
            }
        } catch (final Exception e) {
            setStatus(InstanceStatus.ERROR_CANNOT_STOP);
            processError(e);
        }
    }

    public void terminate() {
        LOG.info(String.format("Terminating cloud openstack instance %s", getName()));
        setStatus(InstanceStatus.STOPPED);
        try {
            if (serverCreated != null) {
                cloudImage.getNovaServerApi().delete(serverCreated.getId());
            }
        } catch (final Exception e) {
            setStatus(InstanceStatus.ERROR_CANNOT_STOP);
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

        private byte[] readUserScriptFile(File userScriptFile) throws IOException {
            try {
                String userScript = FileUtil.readText(userScriptFile);
                // this is userScript actually, but CreateServerOptionscalls it userData
                return userScript.trim().getBytes(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IOException(String.format("Error in reading user script: %s", e.getMessage()), e);
            }
        }

        public void run() {
            try {
                String floatingIp = null;
                if (cloudImage.isAutoFloatingIp()) {
                    // Floating ip should be in meta-data before instance start
                    // If multiple instances start in parallel, perhaps same ip could be retrieved
                    // So an ip reservation mechanism should implemented in this case
                    LOG.debug("Retrieve floating ip for future instance association");
                    floatingIp = cloudImage.getFloatingIpAvailable();
                    if (StringUtil.isEmpty(floatingIp)) {
                        throw new OpenstackException("Floating ip could not be found, cancel instance start");
                    }
                    LOG.debug(String.format("Floating ip: %s", floatingIp));
                    userData.addAgentConfigurationParameter(OpenstackCloudParameters.AGENT_CLOUD_IP, floatingIp);
                }

                String openstackImageId = cloudImage.getOpenstackImageId();
                if (StringUtil.isEmpty(openstackImageId)) {
                    throw new OpenstackException(String.format("No image can be found for name: %s", cloudImage.getOpenstackImageName()));
                }
                String flavorId = cloudImage.getFlavorId();
                CreateServerOptions options = cloudImage.getImageOptions();
                options.metadata(userData.getCustomAgentConfigurationParameters());

                // TODO: that code should be in OpenstackCloudImage but as we make it possible to change userScript without touching teamcity, that
                // hack takes place, sorry
                String userScriptPath = cloudImage.getUserScriptPath();
                if (!Strings.isNullOrEmpty(userScriptPath)) {
                    File pluginData = serverPaths.getPluginDataDirectory();
                    File userScriptFile = new File(new File(pluginData, OpenstackCloudParameters.PLUGIN_SHORT_NAME), userScriptPath);
                    options.userData(readUserScriptFile(userScriptFile)).configDrive(true);
                }

                LOG.debug(String.format("Creating openstack instance with flavorId=%s, imageId=%s, options=%s", flavorId, openstackImageId, options));
                serverCreated = cloudImage.getNovaServerApi().create(getName(), openstackImageId, flavorId, options);

                if (cloudImage.isAutoFloatingIp()) {
                    LOG.debug(String.format("Associating floating ip to serverId %s", serverCreated.getId()));
                    // Associating floating IP. Require fixed IP so wait until found
                    final long maxWait = 120000;
                    final long beginWait = System.currentTimeMillis();
                    while (cloudImage.getNovaServerApi().get(serverCreated.getId()).getAddresses().isEmpty()) {
                        if (System.currentTimeMillis() > (beginWait + maxWait)) {
                            throw new OpenstackException(String.format("Waiting fixed ip fails, taking more than %s ms", maxWait));
                        }
                        LOG.debug(String.format("(Waiting fixed ip before floating ip association on serverId: %s)", serverCreated.getId()));
                        Thread.sleep(1000);
                    }
                    cloudImage.associateFloatingIp(serverCreated.getId(), floatingIp);
                    ip = floatingIp;
                }

                setStatus(InstanceStatus.STARTING);
            } catch (final Exception e) {
                processError(e);
            }
        }
    }
}
