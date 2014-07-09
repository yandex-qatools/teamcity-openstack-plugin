package jetbrains.buildServer.clouds.openstack;

import jetbrains.buildServer.clouds.web.CloudImageDetailsExtensionBase;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

public class OpenstackCloudImageDetailsExtension extends CloudImageDetailsExtensionBase<OpenstackCloudImage> {
  public OpenstackCloudImageDetailsExtension(@NotNull final PagePlaces pagePlaces, @NotNull final PluginDescriptor pluginDescriptor) {
    super(OpenstackCloudImage.class, pagePlaces, pluginDescriptor, "image-details.jsp");
    register();
  }
}
