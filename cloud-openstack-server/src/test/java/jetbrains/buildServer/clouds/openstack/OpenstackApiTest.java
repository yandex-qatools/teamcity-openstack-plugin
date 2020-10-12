package jetbrains.buildServer.clouds.openstack;

import org.testng.Assert;
import org.testng.annotations.Test;

public class OpenstackApiTest {

    @Test
    public void testGetKeystoneVersion() {
        Assert.assertEquals(OpenstackApi.getKeystoneVersion(null), "3");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion(""), "3");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("http://my.openstack.org/v"), "3");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("http://my.openstack.org/v3"), "3");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("http://my.openstack.org/v4"), "4");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("http://my.openstack.org/v2.0"), "2");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("http://my.openstack.org/v2/v3"), "3");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("http://my.openstack.org/v3/v2.0"), "2");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("https://my.openstack.org/v3"), "3");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("https://my.openstack.org/v2.0"), "2");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("https://my.openstack.org/v3/"), "3");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("https://my.openstack.org/v2.0/"), "2");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("https://my.openstack.org/V3"), "3");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("https://my.openstack.org/V2.0"), "2");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("https://my.openstack.org:42/v3"), "3");
        Assert.assertEquals(OpenstackApi.getKeystoneVersion("https://my.openstack.org:42/v2.0"), "2");
    }

}
