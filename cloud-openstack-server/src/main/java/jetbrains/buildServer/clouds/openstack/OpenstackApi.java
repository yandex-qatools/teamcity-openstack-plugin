package jetbrains.buildServer.clouds.openstack;

import java.util.List;
import java.util.Properties;

import org.jclouds.ContextBuilder;
import org.jclouds.location.reference.LocationConstants;
import org.jclouds.openstack.keystone.config.KeystoneProperties;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.NeutronApiMetadata;
import org.jclouds.openstack.neutron.v2.domain.Network;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.NovaApiMetadata;
import org.jclouds.openstack.nova.v2_0.domain.Flavor;
import org.jclouds.openstack.nova.v2_0.domain.Image;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;

import jetbrains.buildServer.util.StringUtil;

public class OpenstackApi {

    private final String zone;

    private final NeutronApi neutronApi;
    private final NovaApi novaApi;

    public OpenstackApi(String endpointUrl, String identity, String password, String zone) {

        // For http content debug during unit tests,
        // - Fill Constants.PROPERTY_LOGGER_WIRE_LOG_SENSITIVE_INFO to true in overrides properties
        // - Add '.modules(ImmutableSet.of(new SLF4JLoggingModule()))' in two ContextBuilder

        this.zone = zone;

        final Properties overrides = new Properties();
        final String keyStoneVersion = getKeystoneVersion(endpointUrl);
        final String[] identityArray = getIdentityArray(identity);
        overrides.setProperty(KeystoneProperties.KEYSTONE_VERSION, keyStoneVersion);
        overrides.setProperty(LocationConstants.PROPERTY_ZONES, zone);
        overrides.setProperty(KeystoneProperties.SCOPE, "project:" + identityArray[1]);

        neutronApi = ContextBuilder.newBuilder(new NeutronApiMetadata())
                .credentials(getCredentialsFromIdentity(identityArray, keyStoneVersion), password).endpoint(endpointUrl).overrides(overrides)
                .buildApi(NeutronApi.class);

        novaApi = ContextBuilder.newBuilder(new NovaApiMetadata()).endpoint(endpointUrl)
                .credentials(getCredentialsFromIdentity(identityArray, keyStoneVersion), password).overrides(overrides).buildApi(NovaApi.class);

    }

    public String getImageIdByName(String name) {
        List<? extends Image> images = novaApi.getImageApi(zone).listInDetail().concat().toList();
        for (Image image : images) {
            if (image.getName().equals(name))
                return image.getId();
        }
        return null;
    }

    public String getFlavorIdByName(String name) {
        List<? extends Flavor> flavors = novaApi.getFlavorApi(zone).listInDetail().concat().toList();
        for (Flavor flavor : flavors) {
            if (flavor.getName().equals(name))
                return flavor.getId();
        }
        return null;
    }

    public String getNetworkIdByName(String name) {
        List<? extends Network> networks = neutronApi.getNetworkApi(zone).list().concat().toList();
        for (Network network : networks) {
            if (network.getName().equals(name))
                return network.getId();
        }
        return null;
    }

    public ServerApi getNovaApi() {
        return novaApi.getServerApi(zone);
    }

    /**
     * Return keystone version (2 or 3) from endpoint URL
     * 
     * @param url endpoint
     * @return 2 or 3
     */
    protected static String getKeystoneVersion(String url) {
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

    /**
     * Get credential identity for corresponding keystone version:<br/>
     * - v2: tenant:user<br/>
     * - v3: domain:user<br/>
     * 
     * @param identity Identity array (should contains 'domain:tenant:user')
     * @param keystoneVersion Keystone version
     * @return The credentials identity
     */
    protected static String getCredentialsFromIdentity(String[] identity, String keystoneVersion) {
        if ("2".equals(keystoneVersion)) {
            return identity[1] + ":" + identity[2];
        }
        return identity[0] + ":" + identity[2];
    }

    /**
     * Get identity as array (split identity string with using :), using 'default' if values not filled
     * 
     * @param identity Identity string (domain:tenant:user)
     * @return Array
     */
    protected static String[] getIdentityArray(String identity) {
        final String[] res = new String[] { "default", "default", "" };
        if (StringUtil.isEmpty(identity)) {
            return res;
        }
        String[] tmp = identity.split(":");
        int diff = res.length - tmp.length;
        for (int i = 0; i < tmp.length; i++) {
            res[i + diff] = tmp[i];
        }
        return res;
    }
}
