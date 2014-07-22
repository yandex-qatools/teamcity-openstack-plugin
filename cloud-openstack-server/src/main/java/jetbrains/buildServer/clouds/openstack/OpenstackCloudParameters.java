package jetbrains.buildServer.clouds.openstack;

import org.jetbrains.annotations.NotNull;

public interface OpenstackCloudParameters {
    @NotNull String CLOUD_TYPE = "NOVA";  //that should be equal or less than 6 symbols, thanks for brainfuck debugging jetbrains guys!
    @NotNull String CLOUD_DISPLAY_NAME = "Openstack Cloud";

    @NotNull String ENDPOINT_URL = "clouds.openstack.endpointUrl";
    @NotNull String IDENTITY = "clouds.openstack.identity";
    @NotNull String PASSWORD = "clouds.openstack.password";
    @NotNull String ZONE = "clouds.openstack.zone";

    @NotNull String IMAGES_PROFILE_SETTING = "clouds.openstack.images";
    @NotNull String IMAGE_ID_PARAM_NAME = "clouds.openstack.imageId";
    @NotNull String INSTANCE_ID_PARAM_NAME = "clouds.openstack.instanceId";
}
