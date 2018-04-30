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

}
