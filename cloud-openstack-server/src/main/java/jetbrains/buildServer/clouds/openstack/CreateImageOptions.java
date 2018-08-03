
package jetbrains.buildServer.clouds.openstack;

import java.util.concurrent.ScheduledExecutorService;

import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.StringUtil;

public class CreateImageOptions {

    @NotNull
    private OpenstackApi openstackApi;
    @NotNull
    private String imageId;
    @NotNull
    private String imageName;
    @NotNull
    private String openstackImageName;
    @NotNull
    private String flavorName;
    @Nullable
    private String volumeName;
    @Nullable
    private String volumeDevice;
    @NotNull
    private boolean autoFloatingIp;
    @NotNull
    private CreateServerOptions createServerOptions;
    @Nullable
    private String userScriptPath;
    @NotNull
    private ServerPaths serverPaths;
    @NotNull
    private ScheduledExecutorService scheduledExecutorService;

    protected CreateImageOptions openstackApi(@NotNull final OpenstackApi openstackApi) {
        this.openstackApi = openstackApi;
        return this;
    }

    protected CreateImageOptions imageId(@NotNull final String imageId) {
        this.imageId = imageId;
        return this;
    }

    protected CreateImageOptions imageName(@NotNull final String imageName) {
        this.imageName = imageName;
        return this;
    }

    protected CreateImageOptions openstackImageName(@NotNull final String openstackImageName) {
        this.openstackImageName = openstackImageName;
        return this;
    }

    protected CreateImageOptions flavorName(@NotNull final String flavorName) {
        this.flavorName = flavorName;
        return this;
    }

    /**
     * Volume should be "volumeName,volumeDevice"
     * 
     * @param volume volume name and volume device
     * @return CreateImageOptions
     */
    protected CreateImageOptions volume(@Nullable final String volume) {
        if (StringUtil.isNotEmpty(volume)) {
            String[] volumeNameDevice = volume.split(",");
            if (volumeNameDevice.length > 0) {
                this.volumeName = volumeNameDevice[0].trim();
            }
            if (volumeNameDevice.length > 1) {
                this.volumeDevice = volumeNameDevice[1].trim();
            }
        }
        return this;
    }

    protected CreateImageOptions autoFloatingIp(@NotNull final boolean autoFloatingIp) {
        this.autoFloatingIp = autoFloatingIp;
        return this;
    }

    protected CreateImageOptions userScriptPath(@Nullable final String userScriptPath) {
        this.userScriptPath = userScriptPath;
        return this;
    }

    protected CreateImageOptions createServerOptions(@NotNull final CreateServerOptions createServerOptions) {
        this.createServerOptions = createServerOptions;
        return this;
    }

    protected CreateImageOptions serverPaths(@NotNull final ServerPaths serverPaths) {
        this.serverPaths = serverPaths;
        return this;
    }

    protected CreateImageOptions scheduledExecutorService(@NotNull final ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
        return this;
    }

    protected OpenstackApi getOpenstackApi() {
        return openstackApi;
    }

    protected String getImageId() {
        return imageId;
    }

    protected String getImageName() {
        return imageName;
    }

    protected String getOpenstackImageName() {
        return openstackImageName;
    }

    protected String getFlavorName() {
        return flavorName;
    }

    protected String getVolumeName() {
        return volumeName;
    }

    protected String getVolumeDevice() {
        return volumeDevice;
    }

    protected boolean isAutoFloatingIp() {
        return autoFloatingIp;
    }

    protected CreateServerOptions getCreateServerOptions() {
        return createServerOptions;
    }

    protected String getUserScriptPath() {
        return userScriptPath;
    }

    protected ServerPaths getServerPaths() {
        return serverPaths;
    }

    protected ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

}
