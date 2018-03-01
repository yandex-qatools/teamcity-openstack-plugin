package jetbrains.buildServer.clouds.openstack;

import org.testng.Assert;
import org.testng.annotations.Test;

public class OpenstackApiTest {

    @Test
    public void testGetKeystoneVersion() {
        Assert.assertEquals("3", OpenstackApi.getKeystoneVersion(null));
        Assert.assertEquals("3", OpenstackApi.getKeystoneVersion(""));
        Assert.assertEquals("3", OpenstackApi.getKeystoneVersion("http://my.openstack.org/v"));
        Assert.assertEquals("3", OpenstackApi.getKeystoneVersion("http://my.openstack.org/v3"));
        Assert.assertEquals("4", OpenstackApi.getKeystoneVersion("http://my.openstack.org/v4"));
        Assert.assertEquals("2", OpenstackApi.getKeystoneVersion("http://my.openstack.org/v2.0"));
        Assert.assertEquals("3", OpenstackApi.getKeystoneVersion("http://my.openstack.org/v2/v3"));
        Assert.assertEquals("2", OpenstackApi.getKeystoneVersion("http://my.openstack.org/v3/v2.0"));
        Assert.assertEquals("3", OpenstackApi.getKeystoneVersion("https://my.openstack.org/v3"));
        Assert.assertEquals("2", OpenstackApi.getKeystoneVersion("https://my.openstack.org/v2.0"));
        Assert.assertEquals("3", OpenstackApi.getKeystoneVersion("https://my.openstack.org/v3/"));
        Assert.assertEquals("2", OpenstackApi.getKeystoneVersion("https://my.openstack.org/v2.0/"));
        Assert.assertEquals("3", OpenstackApi.getKeystoneVersion("https://my.openstack.org/V3"));
        Assert.assertEquals("2", OpenstackApi.getKeystoneVersion("https://my.openstack.org/V2.0"));
        Assert.assertEquals("3", OpenstackApi.getKeystoneVersion("https://my.openstack.org:42/v3"));
        Assert.assertEquals("2", OpenstackApi.getKeystoneVersion("https://my.openstack.org:42/v2.0"));

    }

    @Test
    public void testGetIdentityArray() {
        Assert.assertEquals("default", OpenstackApi.getIdentityArray(null)[0]);
        Assert.assertEquals("default", OpenstackApi.getIdentityArray(null)[1]);
        Assert.assertEquals("", OpenstackApi.getIdentityArray(null)[2]);
        Assert.assertEquals("default", OpenstackApi.getIdentityArray("")[0]);
        Assert.assertEquals("default", OpenstackApi.getIdentityArray("")[1]);
        Assert.assertEquals("", OpenstackApi.getIdentityArray("")[2]);
        Assert.assertEquals("default", OpenstackApi.getIdentityArray("user")[0]);
        Assert.assertEquals("default", OpenstackApi.getIdentityArray("user")[1]);
        Assert.assertEquals("user", OpenstackApi.getIdentityArray("user")[2]);
        Assert.assertEquals("default", OpenstackApi.getIdentityArray("tenant:user")[0]);
        Assert.assertEquals("tenant", OpenstackApi.getIdentityArray("tenant:user")[1]);
        Assert.assertEquals("user", OpenstackApi.getIdentityArray("tenant:user")[2]);
        Assert.assertEquals("domain", OpenstackApi.getIdentityArray("domain:tenant:user")[0]);
        Assert.assertEquals("tenant", OpenstackApi.getIdentityArray("domain:tenant:user")[1]);
        Assert.assertEquals("user", OpenstackApi.getIdentityArray("domain:tenant:user")[2]);
    }

    @Test
    public void testGetCredentialsFromIdentity() {
        Assert.assertEquals("tenant:user", OpenstackApi.getCredentialsFromIdentity(new String[] { "domain", "tenant", "user" }, "2"));
        Assert.assertEquals("domain:user", OpenstackApi.getCredentialsFromIdentity(new String[] { "domain", "tenant", "user" }, "3"));
    }
}
