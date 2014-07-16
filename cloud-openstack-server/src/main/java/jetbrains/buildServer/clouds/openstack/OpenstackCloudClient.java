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
    private final List<OpenstackCloudImage> cloudImages = new ArrayList<OpenstackCloudImage>();
    @Nullable
    private CloudErrorInfo errorInfo;
    @NotNull
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(new NamedDeamonThreadFactory("openstack-cloud-image"));

    public OpenstackCloudClient(@NotNull final CloudClientParameters params) {
        final String images = params.getParameter(OpenstackCloudParameters.IMAGES_PROFILE_SETTING);
        if (images == null || images.trim().length() == 0) {
            errorInfo = new CloudErrorInfo("No images specified");
            return;
        }

        Yaml yaml = new Yaml();
        Map<String, Map> map = (Map) yaml.load(images);

        final IdGenerator imageIdGenerator = new IdGenerator();

        for (Map.Entry<String, Map> entry : map.entrySet()) {

            System.out.println("got image: " + entry.getKey() + "/" + entry.getValue());

            final String imageName = entry.getKey().trim();
            final String openstackImageName = getParameter(entry.getValue(), "image_name");
            final String hardwareName = getParameter(entry.getValue(), "hardware_name");
            final String securityGroupName = getParameter(entry.getValue(), "security_group");
            final String keyPair = getParameter(entry.getValue(), "key_pair");
            final String zone = getParameter(entry.getValue(), "zone");
            final String networkName = getParameter(entry.getValue(), "network");

            OpenstackCloudImage image = new OpenstackCloudImage(imageIdGenerator.next(), imageName, openstackImageName, hardwareName, securityGroupName, keyPair, zone, networkName, executorService);
            cloudImages.add(image);
        }

        //errorInfo = error.length() == 0 ? null : new CloudErrorInfo(error.substring(1));

        System.out.println("cloud client initialized");
        System.out.println("images: " + cloudImages.toString());


    }

    private String getParameter(Map map, String value) {
        String result =  map.get(value).toString().trim();
//        if (result == null || result.length() == 0) {
//            errorInfo = new CloudErrorInfo("No " + value + " specified");
//        }
        return result;
    }

    @NotNull
    public static String generateAgentName(@NotNull final OpenstackCloudImage image, @NotNull final String instanceId) {
        System.out.println("generateAgentName");
        return "openstack-" + image.getName() + "-" + instanceId;
    }

    public boolean isInitialized() {
        System.out.println("isInitialized");
        return true;
    }

    @Nullable
    public OpenstackCloudImage findImageById(@NotNull final String imageId) throws CloudException {
        System.out.println("findImageById");
        for (final OpenstackCloudImage image : cloudImages) {
            if (image.getId().equals(imageId)) {
                return image;
            }
        }
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
        System.out.println("getImages: "+ cloudImages);
        return Collections.unmodifiableList(cloudImages);
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        System.out.println("getErrorInfo");
        return errorInfo;
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
        for (final OpenstackCloudImage image : cloudImages) {
            image.dispose();
        }
        cloudImages.clear();
        executorService.shutdown();
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
