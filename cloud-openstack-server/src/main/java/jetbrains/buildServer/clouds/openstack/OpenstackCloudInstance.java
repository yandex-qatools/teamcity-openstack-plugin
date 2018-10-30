package jetbrains.buildServer.clouds.openstack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.Server.Status;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.diagnostic.Logger;

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
import jetbrains.buildServer.util.StringUtil;

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
    private final OpenstackApi openstackApi;
    @NotNull
    private final Date startDate;
    @Nullable
    private volatile CloudErrorInfo errorInfo;
    @Nullable
    private ServerCreated serverCreated;
    @NotNull
    private final ScheduledExecutorService executor;

    @Nullable
    private String ip;

    private final AtomicReference<InstanceStatus> status = new AtomicReference<>(InstanceStatus.SCHEDULED_TO_START);

    public OpenstackCloudInstance(@NotNull final OpenstackCloudImage image, @NotNull final String instanceId,
            @NotNull final OpenstackApi openstackApi, @NotNull ServerPaths serverPaths, @NotNull ScheduledExecutorService executor) {
        this.openstackApi = openstackApi;
        this.cloudImage = image;
        this.instanceId = instanceId;
        this.serverPaths = serverPaths;
        this.startDate = new Date();
        this.executor = executor;
        setStatus(InstanceStatus.SCHEDULED_TO_START);
    }

    public synchronized void updateStatus() {
        LOG.debug(String.format("Pinging %s for status", getName()));
        if (serverCreated != null) {
            Server server = openstackApi.getServer(serverCreated.getId());
            if (server != null) {
                Server.Status currentStatus = server.getStatus();
                LOG.debug(String.format("Getting instance status from openstack for %s, result is %s", getName(), currentStatus));
                switch (currentStatus) {
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
                case SHELVED:
                case SHELVED_OFFLOADED:
                case SHUTOFF:
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

    @Nullable
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

    public void terminate() {
        LOG.info(String.format("Terminating cloud openstack instance %s", getName()));
        setStatus(InstanceStatus.SCHEDULED_TO_STOP);
        try {
            if (serverCreated != null) {
                openstackApi.deleteServer(serverCreated.getId());
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
                // If floating ip, retrieve one and store in meta-data
                String floatingIp = retrieveAndStoreInMetaDataFloatingIp();

                CreateServerOptions options = cloudImage.getImageOptions();
                options.metadata(userData.getCustomAgentConfigurationParameters());

                // If user script, read it and store in meta-data
                retrieveAndStoreInMetaDataUserScriptFile(options);

                LOG.debug(String.format("Creating openstack instance with flavorId=%s, imageId=%s, options=%s", cloudImage.getFlavorId(),
                        cloudImage.getOpenstackImageId(), options));
                serverCreated = openstackApi.createServer(getName(), cloudImage.getOpenstackImageId(), cloudImage.getFlavorId(), options);

                // Attach volume
                attachVolume();

                // Associate floating ip
                ip = associateFloatingIp(floatingIp);

                setStatus(InstanceStatus.STARTING);
            } catch (final Exception e) {
                processError(e);
            }
        }

        private String associateFloatingIp(String floatingIp) throws InterruptedException, OpenstackException {
            if (!cloudImage.isAutoFloatingIp()) {
                return null;
            }
            LOG.debug(String.format("Associating floating ip to serverId %s", serverCreated.getId()));
            // Associating floating IP. Require fixed IP so wait until found
            final long maxWait = 120000;
            final long beginWait = System.currentTimeMillis();
            while (openstackApi.getServer(serverCreated.getId()).getAddresses().isEmpty()) {
                if (System.currentTimeMillis() > (beginWait + maxWait)) {
                    throw new OpenstackException(String.format("Waiting fixed ip fails, taking more than %s ms", maxWait));
                }
                LOG.debug(String.format("(Waiting fixed ip before floating ip association on serverId: %s)", serverCreated.getId()));
                Thread.sleep(1000);
            }
            openstackApi.associateFloatingIp(serverCreated.getId(), floatingIp); // NOSONAR : Floating IP can't be null here
            return floatingIp;
        }

        private void attachVolume() throws OpenstackException, InterruptedException {
            String volumeName = cloudImage.getVolumeName();
            if (StringUtil.isEmpty(volumeName)) {
                return;
            }
            String volumeDevice = cloudImage.getVolumeDevice();
            LOG.debug(String.format("Attach volume '%s' (with device '%s') to serverId %s", volumeName, volumeDevice, serverCreated.getId()));
            String volumeId = openstackApi.getVolumeIdByName(volumeName);
            if (StringUtil.isEmpty(volumeId)) {
                throw new OpenstackException(String.format("Volume Id can't be retrieved for volume name: %s", volumeName));
            }
            if (StringUtil.isEmpty(volumeDevice)) {
                throw new OpenstackException(String.format("Volume device can't be empty for volume name: %s", volumeName));
            }
            while (!Status.ACTIVE.equals(openstackApi.getServer(serverCreated.getId()).getStatus())) {
                final long maxWait = 120000;
                final long beginWait = System.currentTimeMillis();
                if (System.currentTimeMillis() > (beginWait + maxWait)) {
                    throw new OpenstackException(String.format("Waiting fixed ip fails, taking more than %s ms", maxWait));
                }
                LOG.debug(String.format("(Waiting instance available before volume attachment on serverId: %s)", serverCreated.getId()));
                Thread.sleep(1000);
            }
            openstackApi.attachVolumeToServer(serverCreated.getId(), volumeId, volumeDevice);
        }

        private String retrieveAndStoreInMetaDataFloatingIp() throws OpenstackException {
            if (!cloudImage.isAutoFloatingIp()) {
                return null;
            }
            // Floating ip should be in meta-data before instance start
            // If multiple instances start in parallel, perhaps same ip could be retrieved
            // So an ip reservation mechanism should implemented in this case
            LOG.debug("Retrieve floating ip for future instance association");
            String floatingIp = openstackApi.getFloatingIpAvailable();
            if (StringUtil.isEmpty(floatingIp)) {
                throw new OpenstackException("Floating ip could not be found, cancel instance start");
            }
            LOG.debug(String.format("Floating ip: %s", floatingIp));
            userData.addAgentConfigurationParameter(OpenstackCloudParameters.AGENT_CLOUD_IP, floatingIp);
            return floatingIp;

        }

        private void retrieveAndStoreInMetaDataUserScriptFile(CreateServerOptions options) throws IOException {
            // TODO: that code should be in OpenstackCloudImage but as we make it possible to change userScript without touching teamcity, that
            // hack takes place, sorry
            String userScriptPath = cloudImage.getUserScriptPath();
            if (StringUtil.isEmpty(userScriptPath)) {
                return;
            }
            LOG.debug(String.format("Read user script: %s", userScriptPath));
            File pluginData = serverPaths.getPluginDataDirectory();
            File userScriptFile = new File(new File(pluginData, OpenstackCloudParameters.PLUGIN_SHORT_NAME), userScriptPath);
            options.userData(readUserScriptFile(userScriptFile)).configDrive(true);
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
    }
}
