package jetbrains.buildServer.clouds.openstack;

public final class OpenstackCloudParameters {

    private OpenstackCloudParameters() {
        super();
    }

    public static final String CLOUD_TYPE = "NOVA"; // that should be equal or less than 6 symbols, thanks for brainfuck debugging jetbrains guys!
    public static final String CLOUD_DISPLAY_NAME = "Openstack Cloud";
    public static final String PLUGIN_SHORT_NAME = "openstack";

    public static final String ENDPOINT_URL = "clouds.openstack.endpointUrl";
    public static final String IDENTITY = "clouds.openstack.identity";
    public static final String PASSWORD = "clouds.openstack.password"; // NOSONAR: No clear password
    public static final String REGION = "clouds.openstack.zone";
    public static final String INSTANCE_CAP = "clouds.openstack.instanceCap";

    public static final String AGENT_METADATA_DISABLE = "clouds.openstack.metadata.disable";

    public static final String IMAGES_PROFILES = "clouds.openstack.images";

    public static final String OPENSTACK_INSTANCE_ID = "agent.cloud.uuid";
    public static final String AGENT_CLOUD_TYPE = "agent.cloud.type";
    public static final String AGENT_CLOUD_IP = "agent.cloud.ip";
}
