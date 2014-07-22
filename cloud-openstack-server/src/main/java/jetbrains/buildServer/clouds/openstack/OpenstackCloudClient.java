package jetbrains.buildServer.clouds.openstack;

import com.google.common.io.Closeables;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.util.NamedDeamonThreadFactory;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.Template;
import org.jclouds.location.reference.LocationConstants;
import org.jclouds.openstack.keystone.v2_0.config.CredentialTypes;
import org.jclouds.openstack.keystone.v2_0.config.KeystoneProperties;
import org.jclouds.openstack.nova.v2_0.NovaApiMetadata;
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class OpenstackCloudClient extends BuildServerAdapter implements CloudClientEx {
    @NotNull private final List<OpenstackCloudImage> cloudImages = new ArrayList<OpenstackCloudImage>();
    @NotNull private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new NamedDeamonThreadFactory("openstack-cloud-image"));
    @NotNull private final ComputeService computeService;
    @Nullable private CloudErrorInfo errorInfo = null;

    public OpenstackCloudClient(@NotNull final CloudClientParameters params) {

        final String endpointUrl = params.getParameter(OpenstackCloudParameters.ENDPOINT_URL).trim();
        final String identity = params.getParameter(OpenstackCloudParameters.IDENTITY).trim();
        final String password = params.getParameter(OpenstackCloudParameters.PASSWORD).trim();
        final String zone = params.getParameter(OpenstackCloudParameters.ZONE).trim();

        final Properties overrides = new Properties();
        overrides.setProperty(KeystoneProperties.CREDENTIAL_TYPE, CredentialTypes.PASSWORD_CREDENTIALS);
        overrides.setProperty(Constants.PROPERTY_API_VERSION, "2");
        overrides.setProperty(LocationConstants.PROPERTY_ZONES, zone);

        computeService = ContextBuilder.newBuilder(new NovaApiMetadata())
                .endpoint(endpointUrl)
                .credentials(identity, password)
                .overrides(overrides)
                .buildView(ComputeServiceContext.class)
                .getComputeService();

        final String raw_yaml = params.getParameter(OpenstackCloudParameters.IMAGES_PROFILE_SETTING);
        if (raw_yaml == null || raw_yaml.trim().length() == 0) {
            errorInfo = new CloudErrorInfo("No images specified");
            return;
        }

        Yaml yaml = new Yaml();
        final Map<String, Map> map = (Map) yaml.load(raw_yaml);
        final IdGenerator imageIdGenerator = new IdGenerator();
        final StringBuilder error = new StringBuilder();

        for (Map.Entry<String, Map> entry : map.entrySet()) {

            final String imageName = entry.getKey().trim();
            final String openstackImageName = entry.getValue().get("image_name").toString().trim();
            final String flavorId = entry.getValue().get("flavor_id").toString().trim();
            final String securityGroupName = entry.getValue().get("security_group").toString().trim();
            final String keyPair = entry.getValue().get("key_pair").toString().trim();
            final String networkId = entry.getValue().get("network_id").toString().trim();

            final NovaTemplateOptions options = new NovaTemplateOptions()
                    .networks(networkId)
                    .securityGroupNames(securityGroupName)
                    .keyPairName(keyPair);

            final Template template = computeService.templateBuilder()
                    .imageNameMatches(openstackImageName + "$")
                    .hardwareId(zone + "/" + flavorId)
                    .locationId(zone)
                    .options(options)
                    .build();

            final OpenstackCloudImage image = new OpenstackCloudImage(imageIdGenerator.next(), imageName, computeService, template, executor);
            cloudImages.add(image);

        }

        errorInfo = error.length() == 0 ? null : new CloudErrorInfo(error.substring(1));
    }

    public boolean isInitialized() {
        return true;
    }

    @Nullable
    public OpenstackCloudImage findImageById(@NotNull final String imageId) throws CloudException {
        for (final OpenstackCloudImage image : cloudImages) {
            if (image.getId().equals(imageId)) {
                return image;
            }
        }
        return null;
    }

    @Nullable
    public OpenstackCloudInstance findInstanceByAgent(@NotNull final AgentDescription agentDescription) {
        final OpenstackCloudImage image = findImage(agentDescription);
        if (image == null) return null;

        final String instanceId = findInstanceId(agentDescription);
        if (instanceId == null) return null;

        return image.findInstanceById(instanceId);
    }

    @NotNull
    public Collection<? extends CloudImage> getImages() throws CloudException {
        return Collections.unmodifiableList(cloudImages);
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    public boolean canStartNewInstance(@NotNull final CloudImage image) {
        return true;
    }

    public String generateAgentName(@NotNull final AgentDescription agentDescription) {
        final OpenstackCloudImage image = findImage(agentDescription);
        if (image == null) return null;

        final String instanceId = findInstanceId(agentDescription);
        if (instanceId == null) return null;

        return generateAgentName(image, instanceId);
    }

    @NotNull
    public static String generateAgentName(@NotNull final OpenstackCloudImage image, @NotNull final String instanceId) {
        return OpenstackCloudParameters.CLOUD_TYPE + "-" + image.getName() + "-" + instanceId;
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

    public void dispose() {
        for (final OpenstackCloudImage image : cloudImages) { image.dispose(); }
        cloudImages.clear();
        executor.shutdown();
        Closeables.closeQuietly(computeService.getContext());
    }

    @Nullable
    private OpenstackCloudImage findImage(@NotNull final AgentDescription agentDescription) {
        final String imageId = agentDescription.getConfigurationParameters().get(OpenstackCloudParameters.IMAGE_ID_PARAM_NAME);
        return imageId == null ? null : findImageById(imageId);
    }

    @Nullable
    private String findInstanceId(@NotNull final AgentDescription agentDescription) {
        return agentDescription.getConfigurationParameters().get(OpenstackCloudParameters.INSTANCE_ID_PARAM_NAME);
    }
}
