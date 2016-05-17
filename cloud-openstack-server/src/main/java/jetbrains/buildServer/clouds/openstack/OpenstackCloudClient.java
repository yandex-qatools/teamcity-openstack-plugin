package jetbrains.buildServer.clouds.openstack;

import com.google.common.base.Strings;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

public class OpenstackCloudClient extends BuildServerAdapter implements CloudClientEx {
    @NotNull private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT);
    @NotNull private final List<OpenstackCloudImage> cloudImages = new ArrayList<OpenstackCloudImage>();
    @NotNull private final OpenstackApi openstackApi;
    @Nullable private CloudErrorInfo errorInfo = null;
    @Nullable private final Integer instanceCap;

    public OpenstackCloudClient(@NotNull final CloudClientParameters params,
                                @NotNull final ExecutorServiceFactory factory,
                                @NotNull final ServerPaths serverPaths) {

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
        final Map<String, Map> map = (Map) yaml.load(rawYaml);
        final IdGenerator imageIdGenerator = new IdGenerator();
        final StringBuilder error = new StringBuilder();

        for (Map.Entry<String, Map> entry : map.entrySet()) {

            final String imageName = entry.getKey().trim();
            final String openstackImageName = entry.getValue().get("image").toString().trim();
            final String flavorName = entry.getValue().get("flavor").toString().trim();
            final String securityGroupName = entry.getValue().get("security_group").toString().trim();
            final String keyPair = entry.getValue().get("key_pair").toString().trim();
            final String networkName = entry.getValue().get("network").toString().trim();
            final String userScriptPath = (String) entry.getValue().get("user_script");

            String networkId = openstackApi.getNetworkIdByName(networkName);
            CreateServerOptions options = new CreateServerOptions()
                    .keyPairName(keyPair)
                    .securityGroupNames(securityGroupName)
                    .networks(networkId);

            final String availabilityZone = (String) entry.getValue().get("availability_zone");
            if (!Strings.isNullOrEmpty(availabilityZone)) {
                options.availabilityZone(availabilityZone.trim());
            }

            LOG.debug(String.format(
                "Adding cloud image: imageName=%s, openstackImageName=%s, flavorName=%s, securityGroupName=%s, keyPair=%s, networkName=%s, networkId=%s",
                imageName, openstackImageName, flavorName, securityGroupName, keyPair, networkName, networkId
            ));

            final OpenstackCloudImage image = new OpenstackCloudImage(
                    imageIdGenerator.next(),
                    imageName,
                    openstackApi,
                    openstackImageName,
                    flavorName,
                    options,
                    userScriptPath,
                    factory.createExecutorService(imageName), serverPaths);

            cloudImages.add(image);

        }

        errorInfo = error.length() == 0 ? null : new CloudErrorInfo(error.substring(1));
    }

    public boolean isInitialized() {
        return true;
    }

    @Nullable
    public OpenstackCloudImage findImageById(@NotNull final String imageId) throws CloudException {
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
        if (!configParams.containsValue(OpenstackCloudParameters.CLOUD_TYPE)) return null;

        for (OpenstackCloudImage image: getImages()) {
            for (OpenstackCloudInstance instance: image.getInstances()) {
                if (instance.getOpenstackInstanceId().equals(configParams.get(OpenstackCloudParameters.OPENSTACK_INSTANCE_ID))) {
                    return instance;
                }
            }
        }
        return null;
    }

    @NotNull
    public Collection<? extends OpenstackCloudImage> getImages() throws CloudException {
        return Collections.unmodifiableList(cloudImages);
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    public boolean canStartNewInstance(@NotNull final CloudImage image) {
        if (instanceCap == null) {
            return true;
        } else {
            int i = 0;
            for (final OpenstackCloudImage img : getImages()) {
                i += img.getInstances().size();
            }
            return i < instanceCap;
        }
    }

    @NotNull
    public CloudInstance startNewInstance(@NotNull final CloudImage image, @NotNull final CloudInstanceUserData data) throws QuotaException {
        return ((OpenstackCloudImage)image).startNewInstance(data);
    }

    public void restartInstance(@NotNull final CloudInstance instance) {
        ((OpenstackCloudInstance)instance).restart();
    }

    public void terminateInstance(@NotNull final CloudInstance instance) {
        ((OpenstackCloudInstance)instance).terminate();
    }

    @Nullable
    public String generateAgentName(@NotNull final AgentDescription agentDescription) {
        return null;
    }

    public void dispose() {
        for (final OpenstackCloudImage image : getImages()) image.dispose();
        cloudImages.clear();
    }
}
