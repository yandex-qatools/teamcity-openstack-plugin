package jetbrains.buildServer.clouds.openstack;

import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.location.reference.LocationConstants;
import org.jclouds.openstack.keystone.v2_0.config.CredentialTypes;
import org.jclouds.openstack.keystone.v2_0.config.KeystoneProperties;
import org.jclouds.openstack.neutron.v2_0.NeutronApi;
import org.jclouds.openstack.neutron.v2_0.NeutronApiMetadata;
import org.jclouds.openstack.neutron.v2_0.domain.Network;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.NovaApiMetadata;
import org.jclouds.openstack.nova.v2_0.domain.Flavor;
import org.jclouds.openstack.nova.v2_0.domain.Image;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;

import java.util.List;
import java.util.Properties;


public class OpenstackApi {

    protected final String endpointUrl;
    protected final String identity;
    protected final String password;

    private final String zone;

    private NeutronApi neutronApi;
    private NovaApi novaApi;

    public OpenstackApi(String endpointUrl, String identity, String password, String zone) {

        this.endpointUrl = endpointUrl;
        this.identity = identity;
        this.password = password;
        this.zone = zone;

        final Properties overrides = new Properties();
        overrides.setProperty(KeystoneProperties.CREDENTIAL_TYPE, CredentialTypes.PASSWORD_CREDENTIALS);
        overrides.setProperty(Constants.PROPERTY_API_VERSION, "2");
        overrides.setProperty(LocationConstants.PROPERTY_ZONES, zone);

        novaApi = ContextBuilder.newBuilder(new NovaApiMetadata())
                .endpoint(endpointUrl)
                .credentials(identity, password)
                .overrides(overrides)
                .buildApi(NovaApi.class);

        neutronApi = ContextBuilder.newBuilder(new NeutronApiMetadata())
                .credentials(identity, password)
                .endpoint(endpointUrl)
                .overrides(overrides)
                .buildApi(NeutronApi.class);

    }

    public String getImageIdByName(String name) {
        List<? extends Image> images = novaApi.getImageApiForZone(zone).listInDetail().concat().toList();
        for (Image image: images) {
            if (image.getName().equals(name)) return image.getId();
        }
        return null;
    }

    public String getFlavorIdByName(String name) {
        List<? extends Flavor> flavors = novaApi.getFlavorApiForZone(zone).listInDetail().concat().toList();
        for (Flavor flavor: flavors) {
            if (flavor.getName().equals(name)) return flavor.getId();
        }
        return null;
    }

    public String getNetworkIdByName(String name) {
        List<? extends Network> networks = neutronApi.getNetworkApiForZone(zone).listInDetail().concat().toList();
        for (Network network: networks) {
            if (network.getName().equals(name)) return network.getId();
        }
        return null;
    }

    public ServerApi getNovaApi() {
        return novaApi.getServerApiForZone(zone);
    }
}
