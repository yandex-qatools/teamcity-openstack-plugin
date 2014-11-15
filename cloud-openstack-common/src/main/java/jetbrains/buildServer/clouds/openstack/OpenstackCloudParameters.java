package jetbrains.buildServer.clouds.openstack;


public interface OpenstackCloudParameters {
    String CLOUD_TYPE = "NOVA";  //that should be equal or less than 6 symbols, thanks for brainfuck debugging jetbrains guys!
    String CLOUD_DISPLAY_NAME = "Openstack Cloud";
    String PLUGIN_SHORT_NAME = "openstack";

    String ENDPOINT_URL = "clouds.openstack.endpointUrl";
    String IDENTITY = "clouds.openstack.identity";
    String PASSWORD = "clouds.openstack.password";
    String REGION = "clouds.openstack.zone";
    String INSTANCE_CAP = "clouds.openstack.instanceCap";

    String IMAGES_PROFILES = "clouds.openstack.images";

    String OPENSTACK_INSTANCE_ID = "agent.cloud.uuid";
    String AGENT_CLOUD_TYPE = "agent.cloud.type";
}
