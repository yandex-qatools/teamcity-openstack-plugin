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
    @NotNull
    private final List<OpenstackCloudImage> myImages = new ArrayList<OpenstackCloudImage>();
    @Nullable
    private final CloudErrorInfo myErrorInfo;
    @NotNull
    private final ScheduledExecutorService myExecutor = Executors.newSingleThreadScheduledExecutor(new NamedDeamonThreadFactory("openstack-cloud-image"));

    public OpenstackCloudClient(@NotNull final CloudClientParameters params) {
        final String images = params.getParameter(OpenstackCloudParameters.IMAGES_PROFILE_SETTING);
        if (images == null || images.trim().length() == 0) {
            myErrorInfo = new CloudErrorInfo("No images specified");
            return;
        }

        Yaml yaml = new Yaml();
        Map<String, Map> map = (Map) yaml.load(images);

        final IdGenerator imageIdGenerator = new IdGenerator();
        final StringBuilder error = new StringBuilder();

        for (Map.Entry<String, Map> entry : map.entrySet()) {

            System.out.println("got image: " + entry.getKey() + "/" + entry.getValue());

            final String imageName = entry.getKey().trim();
            final String openstackImageName = entry.getValue().get("image_name").toString().trim();
            final String hardwareName = entry.getValue().get("hardware_name").toString().trim();
            final String securityGroupName = entry.getValue().get("security_group").toString().trim();
            final String keyPair = entry.getValue().get("key_pair").toString().trim();
            final String zone = entry.getValue().get("zone").toString().trim();
            final String networkName = entry.getValue().get("network").toString().trim();

            final OpenstackCloudImage image = new OpenstackCloudImage(imageIdGenerator.next(), imageName, openstackImageName, hardwareName, securityGroupName, keyPair, zone, networkName, myExecutor);
            myImages.add(image);
        }

        myErrorInfo = error.length() == 0 ? null : new CloudErrorInfo(error.substring(1));

        System.out.println("cloud client initialized");
        System.out.println("images: " + myImages.toString());
    }

    @NotNull
    public static String generateAgentName(@NotNull final OpenstackCloudImage image, @NotNull final String instanceId) {
        System.out.println("generateAgentName");
        return "img-" + image.getName() + "-" + instanceId;
    }

    public boolean isInitialized() {
        System.out.println("isInitialized");
        return true;
    }

    @Nullable
    public OpenstackCloudImage findImageById(@NotNull final String imageId) throws CloudException {
        for (final OpenstackCloudImage image : myImages) {
            if (image.getId().equals(imageId)) {
                return image;
            }
        }
        System.out.println("findImageById");
        return null;
    }

    @Nullable
    public OpenstackCloudInstance findInstanceByAgent(@NotNull final AgentDescription agentDescription) {
        System.out.println("findInstanceByAgent");
        final OpenstackCloudImage image = findImage(agentDescription);
        if (image == null) return null;

        final String instanceId = findInstanceId(agentDescription);
        if (instanceId == null) return null;

        return image.findInstanceById(instanceId);
    }

    @NotNull
    public Collection<? extends CloudImage> getImages() throws CloudException {
        System.out.println("getImages: "+ myImages);
        return Collections.unmodifiableList(myImages);
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        System.out.println("getErrorInfo");
        return myErrorInfo;
    }

    public boolean canStartNewInstance(@NotNull final CloudImage image) {
        System.out.println("canStartNewInstance");
        return true;
    }

    public String generateAgentName(@NotNull final AgentDescription agentDescription) {
        System.out.println("generateAgentName");
        final OpenstackCloudImage image = findImage(agentDescription);
        if (image == null) return null;

        final String instanceId = findInstanceId(agentDescription);
        if (instanceId == null) return null;

        return generateAgentName(image, instanceId);
    }

    @NotNull
    public CloudInstance startNewInstance(@NotNull final CloudImage image, @NotNull final CloudInstanceUserData data) throws QuotaException {
        System.out.println("startNewInstance");
        return ((OpenstackCloudImage) image).startNewInstance(data);
    }

    public void restartInstance(@NotNull final CloudInstance instance) {
        System.out.println("restartInstance");
        ((OpenstackCloudInstance) instance).restart();
    }

    public void terminateInstance(@NotNull final CloudInstance instance) {
        System.out.println("terminateInstance");
        ((OpenstackCloudInstance) instance).terminate();
    }

    public void dispose() {
        System.out.println("dispose");
        for (final OpenstackCloudImage image : myImages) {
            image.dispose();
        }
        myImages.clear();
        myExecutor.shutdown();
    }

    @Nullable
    private OpenstackCloudImage findImage(@NotNull final AgentDescription agentDescription) {
        System.out.println("findImage");
        final String imageId = agentDescription.getConfigurationParameters().get(OpenstackCloudParameters.IMAGE_ID_PARAM_NAME);
        return imageId == null ? null : findImageById(imageId);
    }

    @Nullable
    private String findInstanceId(@NotNull final AgentDescription agentDescription) {
        System.out.println("findInstanceId");
        return agentDescription.getConfigurationParameters().get(OpenstackCloudParameters.INSTANCE_ID_PARAM_NAME);
    }

}
