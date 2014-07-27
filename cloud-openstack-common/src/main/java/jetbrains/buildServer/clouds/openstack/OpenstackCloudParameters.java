package jetbrains.buildServer.clouds.openstack;


public interface OpenstackCloudParameters {
    String CLOUD_TYPE = "NOVA";  //that should be equal or less than 6 symbols, thanks for brainfuck debugging jetbrains guys!
    String CLOUD_DISPLAY_NAME = "Openstack Cloud";

    String ENDPOINT_URL = "clouds.openstack.endpointUrl";
    String IDENTITY = "clouds.openstack.identity";
    String PASSWORD = "clouds.openstack.password";
    String ZONE = "clouds.openstack.zone";

    String IMAGES_PROFILE_SETTING = "clouds.openstack.images";

    String IMAGE_ID_PARAM_NAME = "clouds.openstack.imageId";
    String INSTANCE_ID_PARAM_NAME = "clouds.openstack.instanceId";
}
