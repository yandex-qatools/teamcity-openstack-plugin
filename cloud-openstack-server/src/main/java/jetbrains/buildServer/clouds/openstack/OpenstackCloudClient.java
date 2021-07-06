package jetbrains.buildServer.clouds.openstack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ObjectUtils;
import com.jcabi.log.VerboseRunnable;

import jetbrains.buildServer.clouds.CanStartNewInstanceResult;
import jetbrains.buildServer.clouds.CloudClientEx;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.StringUtil;

public class OpenstackCloudClient extends BuildServerAdapter implements CloudClientEx {
    @NotNull
    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT);
    @NotNull
    private final List<OpenstackCloudImage> cloudImages = new ArrayList<>();
    @NotNull
    private final OpenstackApi openstackApi;
    @Nullable
    private CloudErrorInfo errorInfo = null;
    @Nullable
    private final Integer instanceCap;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> initialized;

    public OpenstackCloudClient(@NotNull final CloudClientParameters params, @NotNull ServerPaths serverPaths,
            @NotNull final ExecutorServiceFactory factory) {

        final String endpointUrl = params.getParameter(OpenstackCloudParameters.ENDPOINT_URL).trim();
        final String identity = params.getParameter(OpenstackCloudParameters.IDENTITY).trim();
        final String password = params.getParameter(OpenstackCloudParameters.PASSWORD).trim();
        final String region = params.getParameter(OpenstackCloudParameters.REGION).trim();

        instanceCap = Integer.parseInt(params.getParameter(OpenstackCloudParameters.INSTANCE_CAP));
        openstackApi = new OpenstackApi(endpointUrl, identity, password, region);

        final String rawYaml = params.getParameter(OpenstackCloudParameters.IMAGES_PROFILES);
        LOG.debug(String.format("Using the following cloud parameters: endpointUrl=%s, identity=%s, zone=%s", endpointUrl, identity, region));
        if (rawYaml == null || rawYaml.trim().length() == 0) {
            errorInfo = new CloudErrorInfo("No images specified");
            return;
        }
        LOG.debug(String.format("Using the following YAML data: %s", rawYaml));

        Yaml yaml = new Yaml();
        final Map<String, Map<String, String>> map = yaml.load(rawYaml);
        if (map == null || map.isEmpty()) {
            errorInfo = new CloudErrorInfo("No images specified (perhaps only comments)");
            return;
        }

        LOG.info(String.format("Testing credentials by retrieving servers list status (identity: %s)...", identity));
        openstackApi.getNovaServerApi().listInDetail();

        final StringBuilder error = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> entry : map.entrySet()) {
            final String imageName = entry.getKey().trim();
            if (entry.getValue() == null) {
                errorInfo = new CloudErrorInfo(String.format("No parameters defined for image: %s", imageName));
                return;
            }
            final String openstackImageName = StringUtil.trim(entry.getValue().get("image"));
            final String flavorName = StringUtil.trim(entry.getValue().get("flavor"));
            final String networkName = StringUtil.trim(entry.getValue().get("network"));
            final String securityGroupName = StringUtil.trim(entry.getValue().get("security_group"));
            final String keyPair = StringUtil.trim(entry.getValue().get("key_pair"));
            final String userScriptPath = entry.getValue().get("user_script");
            Boolean autoFloatingIp = (Boolean) (Object) entry.getValue().get("auto_floating_ip"); // Evil, but Yaml parse Boolean only for this
            autoFloatingIp = ObjectUtils.chooseNotNull(autoFloatingIp, false); // Can be null if not defined

            String networkId = openstackApi.getNetworkIdByName(networkName);
            CreateServerOptions options = new CreateServerOptions().keyPairName(keyPair).securityGroupNames(securityGroupName).networks(networkId);

            final String availabilityZone = entry.getValue().get("availability_zone");
            if (!Strings.isNullOrEmpty(availabilityZone)) {
                options.availabilityZone(availabilityZone.trim());
            }

            LOG.debug(String.format(
                    "Adding cloud image: imageName=%s, openstackImageName=%s, flavorName=%s, networkName=%s, networkId=%s, securityGroupName=%s, keyPair=%s, floatingIp=%s",
                    imageName, openstackImageName, flavorName, networkName, networkId, securityGroupName, keyPair, autoFloatingIp));

            LOG.info(String.format("Create image  [%s] ...", imageName));
            final OpenstackCloudImage image = new OpenstackCloudImage(openstackApi, imageName /* imageIdGenerator.next() */, imageName,
                    openstackImageName, flavorName, autoFloatingIp, options, userScriptPath, serverPaths, factory.createExecutorService(imageName));

            cloudImages.add(image);

        }

        errorInfo = error.length() == 0 ? null : new CloudErrorInfo(error.substring(1));

        // start asynchronous initialization:
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.initialized = this.executor.schedule(new VerboseRunnable(() -> {
            for (OpenstackCloudImage cloudImage : cloudImages) {
                cloudImage.initialize();
            }
        }, true), 1, TimeUnit.SECONDS);
    }

    @Override
    public boolean isInitialized() {
        // wait for initialization completion:
        if (this.initialized != null) {
            try {
                this.initialized.get((long) cloudImages.size() * 3, TimeUnit.SECONDS);
                if (this.executor != null) {
                    executor.shutdown();
                    executor = null;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException ex) {
                LOG.error(String.format("Initialization failure: %s: %s", ex.getClass().getSimpleName(), ex.getMessage()));
            }
        }
        return true;
    }

    @Nullable
    public OpenstackCloudImage findImageById(@NotNull final String imageId) {
        for (final OpenstackCloudImage image : getImages()) {
            if (image.getId().equals(imageId)) {
                return image;
            }
        }
        return null;
    }

    @Nullable
    public OpenstackCloudInstance findInstanceByAgent(@NotNull final AgentDescription agentDescription) {
        final Map<String, String> configParams = agentDescription.getConfigurationParameters();
        if (!configParams.containsValue(OpenstackCloudParameters.CLOUD_TYPE)) {
            return null;
        }
        for (OpenstackCloudImage image : getImages()) {
            for (OpenstackCloudInstance instance : image.getInstances()) {
                if (instance.getOpenstackInstanceId().equals(configParams.get(OpenstackCloudParameters.OPENSTACK_INSTANCE_ID))) {
                    return instance;
                }
            }
        }
        return null;
    }

    @NotNull
    public Collection<? extends OpenstackCloudImage> getImages() {
        return Collections.unmodifiableList(cloudImages);
    }

    @Nullable
    @Override
    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    /**
     * Deprecated since 2018.1. jetbrains.buildServer.clouds.CloudClient#canStartNewInstanceWithDetails(jetbrains.buildServer.clouds.CloudImage) is
     * being used instead.
     * 
     * @deprecated
     * @see jetbrains.buildServer.clouds.CloudClient#canStartNewInstance(jetbrains.buildServer.clouds.CloudImage)
     */
    @Override
    @Deprecated
    public boolean canStartNewInstance(@NotNull final CloudImage image) { // TODO: NOSONAR Should work with 2017 and 2020
        if (instanceCap == null) {
            return true;
        }
        int i = 0;
        for (final OpenstackCloudImage img : getImages()) {
            i += img.getInstances().size();
        }
        return i < instanceCap;
    }

    @Override
    public CanStartNewInstanceResult canStartNewInstanceWithDetails(@NotNull final CloudImage image) {
        if (canStartNewInstance(image)) { // TODO: NOSONAR Should work with 2017 and 2020
            return CanStartNewInstanceResult.yes();
        }
        return CanStartNewInstanceResult.no("Instance cap exceeded");
    }

    @NotNull
    public CloudInstance startNewInstance(@NotNull final CloudImage image, @NotNull final CloudInstanceUserData data) {
        return ((OpenstackCloudImage) image).startNewInstance(data);
    }

    @Override
    public void restartInstance(@NotNull final CloudInstance instance) {
        ((OpenstackCloudInstance) instance).restart();
    }

    public void terminateInstance(@NotNull final CloudInstance instance) {
        ((OpenstackCloudInstance) instance).stop();
    }

    @Nullable
    public String generateAgentName(@NotNull final AgentDescription agentDescription) {
        return null;
    }

    @Override
    public void dispose() {
        for (final OpenstackCloudImage image : getImages()) {
            image.dispose();
        }
        cloudImages.clear();
        if (executor != null)
            executor.shutdown();
    }

}
