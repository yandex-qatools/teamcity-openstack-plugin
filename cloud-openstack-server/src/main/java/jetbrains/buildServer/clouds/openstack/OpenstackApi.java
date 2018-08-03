package jetbrains.buildServer.clouds.openstack;

import java.util.List;
import java.util.Properties;

import org.jclouds.ContextBuilder;
import org.jclouds.location.reference.LocationConstants;
import org.jclouds.openstack.cinder.v1.CinderApi;
import org.jclouds.openstack.cinder.v1.CinderApiMetadata;
import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.jclouds.openstack.keystone.config.KeystoneProperties;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.NeutronApiMetadata;
import org.jclouds.openstack.neutron.v2.domain.FloatingIP;
import org.jclouds.openstack.neutron.v2.domain.Network;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.NovaApiMetadata;
import org.jclouds.openstack.nova.v2_0.domain.Flavor;
import org.jclouds.openstack.nova.v2_0.domain.Image;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import jetbrains.buildServer.util.StringUtil;

public class OpenstackApi {

    private final String region;

    private final NeutronApi neutronApi;
    private final NovaApi novaApi;
    private final CinderApi cinderApi;

    public OpenstackApi(String endpointUrl, String identity, String password, String region) {

        // For http content debug during unit tests,
        // - Fill Constants.PROPERTY_LOGGER_WIRE_LOG_SENSITIVE_INFO to true in overrides properties
        // - Add '.modules(ImmutableSet.of(new SLF4JLoggingModule()))' in two ContextBuilder
        // - Update log level to 'DEBUG' in 'log4j.xml'.

        this.region = region;

        final Properties overrides = new Properties();
        final String keyStoneVersion = getKeystoneVersion(endpointUrl);
        final OpenstackIdentity identityObject = new OpenstackIdentity(identity, keyStoneVersion);
        overrides.put(KeystoneProperties.KEYSTONE_VERSION, keyStoneVersion);
        overrides.put(LocationConstants.PROPERTY_ZONES, region);

        if (!StringUtil.isEmpty(identityObject.getTenant())) {
            // Only for keystone v3, for v2 'tenant' is part of Credentials (cf. OpenstackIdentity)
            overrides.put(KeystoneProperties.SCOPE, "project:" + identityObject.getTenant());
        }
        if (!StringUtil.isEmpty(identityObject.getTenantDomain())) {
            overrides.put(KeystoneProperties.TENANT_ID, identityObject.getTenantDomain());
        }

        neutronApi = ContextBuilder.newBuilder(new NeutronApiMetadata()).credentials(identityObject.getCredendials(), password).endpoint(endpointUrl)
                .overrides(overrides).buildApi(NeutronApi.class);

        novaApi = ContextBuilder.newBuilder(new NovaApiMetadata()).endpoint(endpointUrl).credentials(identityObject.getCredendials(), password)
                .overrides(overrides).buildApi(NovaApi.class);

        cinderApi = ContextBuilder.newBuilder(new CinderApiMetadata()).endpoint(endpointUrl).credentials(identityObject.getCredendials(), password)
                .overrides(overrides).buildApi(CinderApi.class);

    }

    @Nullable
    public Server getServer(@NotNull final String id) {
        return novaApi.getServerApi(region).get(id);
    }

    @NotNull
    public ServerCreated createServer(@NotNull final String name, @NotNull final String imageId, @NotNull final String flavorId,
            @NotNull final CreateServerOptions options) {
        return novaApi.getServerApi(region).create(name, imageId, flavorId, options);

    }

    public void deleteServer(@NotNull final String id) {
        novaApi.getServerApi(region).delete(id);
    }

    public void attachVolumeToServer(@NotNull final String serverId, @NotNull final String volumeId, @NotNull final String volumeDevice) {
        novaApi.getVolumeAttachmentApi(region).get().attachVolumeToServerAsDevice(volumeId, serverId, volumeDevice);
    }

    @Nullable
    public String getImageIdByName(@NotNull final String name) {
        List<? extends Image> images = novaApi.getImageApi(region).listInDetail().concat().toList();
        for (Image image : images) {
            if (image.getName().equals(name))
                return image.getId();
        }
        return null;
    }

    @Nullable
    public String getFlavorIdByName(@NotNull final String name) {
        List<? extends Flavor> flavors = novaApi.getFlavorApi(region).listInDetail().concat().toList();
        for (Flavor flavor : flavors) {
            if (flavor.getName().equals(name))
                return flavor.getId();
        }
        return null;
    }

    @Nullable
    public String getNetworkIdByName(@NotNull final String name) {
        List<? extends Network> networks = neutronApi.getNetworkApi(region).list().concat().toList();
        for (Network network : networks) {
            if (network.getName().equals(name))
                return network.getId();
        }
        return null;
    }

    @Nullable
    public String getVolumeIdByName(@NotNull final String name) {
        List<? extends Volume> volumes = cinderApi.getVolumeApi(region).list().toList();
        for (Volume volume : volumes) {
            if (volume.getName().equals(name))
                return volume.getId();
        }
        return null;
    }

    public void associateFloatingIp(@NotNull final String serverId, @NotNull final String ip) {
        novaApi.getFloatingIPApi(region).get().addToServer(ip, serverId);
    }

    @Nullable
    public String getFloatingIpAvailable() {
        for (FloatingIP ip : neutronApi.getFloatingIPApi(region).list().concat().toList()) {
            if (StringUtil.isEmpty(ip.getFixedIpAddress())) {
                return ip.getFloatingIpAddress();
            }
        }
        return null;
    }

    /**
     * Return keystone version (2 or 3) from endpoint URL
     * 
     * @param url endpoint
     * @return 2 or 3
     */
    protected static String getKeystoneVersion(@NotNull final String url) {
        final String def = "3";
        if (StringUtil.isEmpty(url)) {
            return def;
        }
        int index = url.toLowerCase().lastIndexOf("/v") + 2;
        if (url.length() > index) {
            return url.substring(index, index + 1);
        }
        return def;
    }

}
