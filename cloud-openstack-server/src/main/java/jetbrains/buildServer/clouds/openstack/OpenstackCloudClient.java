package jetbrains.buildServer.clouds.openstack;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.util.NamedDeamonThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class OpenstackCloudClient extends BuildServerAdapter implements CloudClientEx {
    @NotNull private final List<OpenstackCloudImage> cloudImages = new ArrayList<OpenstackCloudImage>();
    @Nullable private final CloudErrorInfo errorInfo;
    @NotNull private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new NamedDeamonThreadFactory("openstack-cloud-image"));

    public OpenstackCloudClient(@NotNull final CloudClientParameters params) {
        final String images = params.getParameter(OpenstackCloudParameters.IMAGES_PROFILE_SETTING);
        if (images == null || images.trim().length() == 0) {
            errorInfo = new CloudErrorInfo("No images specified");
            return;
        }

        Yaml yaml = new Yaml();
        Map<String, Map> map = (Map) yaml.load(images);

        final IdGenerator imageIdGenerator = new IdGenerator();
        final StringBuilder error = new StringBuilder();

        for (Map.Entry<String, Map> entry : map.entrySet()) {

            final String imageName = entry.getKey().trim();
            final String openstackImageName = entry.getValue().get("image_name").toString().trim();
            final String hardwareName = entry.getValue().get("hardware_name").toString().trim();
            final String securityGroupName = entry.getValue().get("security_group").toString().trim();
            final String keyPair = entry.getValue().get("key_pair").toString().trim();
            final String zone = entry.getValue().get("zone").toString().trim();
            final String networkName = entry.getValue().get("network").toString().trim();

            //TODO: get rid of that shit
            final String agentHomePath = entry.getValue().get("agentHomePath").toString().trim();

            final OpenstackCloudImage image = new OpenstackCloudImage(imageIdGenerator.next(), imageName, agentHomePath, openstackImageName, hardwareName, securityGroupName, keyPair, zone, networkName, executor);
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
        return "img-" + image.getName() + "-" + instanceId;
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
        for (final OpenstackCloudImage image : cloudImages) {
            image.dispose();
        }
        cloudImages.clear();
        executor.shutdown();
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
