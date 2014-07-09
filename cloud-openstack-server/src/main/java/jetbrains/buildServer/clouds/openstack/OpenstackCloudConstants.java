package jetbrains.buildServer.clouds.openstack;

import org.jetbrains.annotations.NotNull;

public interface OpenstackCloudConstants {
    @NotNull String CLOUD_TYPE = "Openstack";
    @NotNull String CLOUD_DISPLAY_NAME = "Openstack Cloud";
    @NotNull String IMAGES_PROFILE_SETTING = "images";
    @NotNull String IMAGE_ID_PARAM_NAME = "clouds.openstack.image.id";
    @NotNull String INSTANCE_ID_PARAM_NAME = "clouds.openstack.instance.id";
}
