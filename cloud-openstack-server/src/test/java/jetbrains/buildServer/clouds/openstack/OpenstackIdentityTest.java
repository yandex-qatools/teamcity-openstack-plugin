package jetbrains.buildServer.clouds.openstack;

import org.testng.Assert;
import org.testng.annotations.Test;

public class OpenstackIdentityTest {

    @Test
    public void testOpenstackIdentityNullOrEmpty() {
        Assert.assertEquals("", new OpenstackIdentity("", null).getCredendials());
        Assert.assertEquals("", new OpenstackIdentity("", "").getCredendials());
        Assert.assertEquals("", new OpenstackIdentity("", "3").getCredendials());
    }

    @Test
    public void testOpenstackIdentityV2() {
        Assert.assertEquals("", new OpenstackIdentity("", "2").getCredendials());
        Assert.assertEquals("user", new OpenstackIdentity("user", "2").getCredendials());

        Assert.assertEquals("tenant:user", new OpenstackIdentity("tenant:user", "2").getCredendials());
        Assert.assertEquals(null, new OpenstackIdentity("tenant:user", "2").getTenant());
        Assert.assertEquals(null, new OpenstackIdentity("tenant:user", "2").getTenantDomain());

        Assert.assertEquals("tenant:user", new OpenstackIdentity("fake:tenant:user", "2").getCredendials());
        Assert.assertEquals(null, new OpenstackIdentity("fake:tenant:user", "2").getTenant());
        Assert.assertEquals(null, new OpenstackIdentity("fake:tenant:user", "2").getTenantDomain());
    }

    @Test
    public void testOpenstackIdentityV3() {
        Assert.assertEquals("", new OpenstackIdentity("", "3").getCredendials());
        Assert.assertEquals("user", new OpenstackIdentity("user", "3").getCredendials());

        Assert.assertEquals("user", new OpenstackIdentity("tenant:user", "3").getCredendials());
        Assert.assertEquals("tenant", new OpenstackIdentity("tenant:user", "3").getTenant());
        Assert.assertEquals(null, new OpenstackIdentity("tenant:user", "3").getTenantDomain());

        Assert.assertEquals("domain_user:user", new OpenstackIdentity("tenant:domain_user:user", "3").getCredendials());
        Assert.assertEquals("tenant", new OpenstackIdentity("tenant:domain_user:user", "3").getTenant());
        Assert.assertEquals(null, new OpenstackIdentity("tenant:domain_user:user", "3").getTenantDomain());

        Assert.assertEquals("domain_user:user", new OpenstackIdentity("domain_tenant:tenant:domain_user:user", "3").getCredendials());
        Assert.assertEquals("tenant", new OpenstackIdentity("domain_tenant:tenant:domain_user:user", "3").getTenant());
        Assert.assertEquals("domain_tenant", new OpenstackIdentity("domain_tenant:tenant:domain_user:user", "3").getTenantDomain());

        Assert.assertEquals("domain_user:user", new OpenstackIdentity("fake:domain_tenant:tenant:domain_user:user", "3").getCredendials());
        Assert.assertEquals("tenant", new OpenstackIdentity("fake:domain_tenant:tenant:domain_user:user", "3").getTenant());
        Assert.assertEquals("domain_tenant", new OpenstackIdentity("fake:domain_tenant:tenant:domain_user:user", "3").getTenantDomain());
    }
}
