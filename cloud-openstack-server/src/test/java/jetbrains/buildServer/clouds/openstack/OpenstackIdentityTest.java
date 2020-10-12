package jetbrains.buildServer.clouds.openstack;

import org.testng.Assert;
import org.testng.annotations.Test;

public class OpenstackIdentityTest {

    @Test
    public void testOpenstackIdentityNullOrEmpty() {
        Assert.assertEquals(new OpenstackIdentity("", null).getCredendials(), "");
        Assert.assertEquals(new OpenstackIdentity("", "").getCredendials(), "");
        Assert.assertEquals(new OpenstackIdentity("", "3").getCredendials(), "");
    }

    @Test
    public void testOpenstackIdentityV2() {
        Assert.assertEquals(new OpenstackIdentity("", "2").getCredendials(), "");
        Assert.assertEquals(new OpenstackIdentity("user", "2").getCredendials(), "user");

        Assert.assertEquals(new OpenstackIdentity("tenant:user", "2").getCredendials(), "tenant:user");
        Assert.assertNull(new OpenstackIdentity("tenant:user", "2").getTenant());
        Assert.assertNull(new OpenstackIdentity("tenant:user", "2").getTenantDomain());

        Assert.assertEquals(new OpenstackIdentity("fake:tenant:user", "2").getCredendials(), "tenant:user");
        Assert.assertNull(new OpenstackIdentity("fake:tenant:user", "2").getTenant());
        Assert.assertNull(new OpenstackIdentity("fake:tenant:user", "2").getTenantDomain());
    }

    @Test
    public void testOpenstackIdentityV3() {
        Assert.assertEquals(new OpenstackIdentity("", "3").getCredendials(), "");
        Assert.assertEquals(new OpenstackIdentity("user", "3").getCredendials(), "user");

        Assert.assertEquals(new OpenstackIdentity("tenant:user", "3").getCredendials(), "user");
        Assert.assertEquals(new OpenstackIdentity("tenant:user", "3").getTenant(), "tenant");
        Assert.assertNull(new OpenstackIdentity("tenant:user", "3").getTenantDomain());

        Assert.assertEquals(new OpenstackIdentity("tenant:domain_user:user", "3").getCredendials(), "domain_user:user");
        Assert.assertEquals(new OpenstackIdentity("tenant:domain_user:user", "3").getTenant(), "tenant");
        Assert.assertNull(new OpenstackIdentity("tenant:domain_user:user", "3").getTenantDomain());

        Assert.assertEquals(new OpenstackIdentity("domain_tenant:tenant:domain_user:user", "3").getCredendials(), "domain_user:user");
        Assert.assertEquals(new OpenstackIdentity("domain_tenant:tenant:domain_user:user", "3").getTenant(), "tenant");
        Assert.assertEquals(new OpenstackIdentity("domain_tenant:tenant:domain_user:user", "3").getTenantDomain(), "domain_tenant");

        Assert.assertEquals(new OpenstackIdentity("fake:domain_tenant:tenant:domain_user:user", "3").getCredendials(), "domain_user:user");
        Assert.assertEquals(new OpenstackIdentity("fake:domain_tenant:tenant:domain_user:user", "3").getTenant(), "tenant");
        Assert.assertEquals(new OpenstackIdentity("fake:domain_tenant:tenant:domain_user:user", "3").getTenantDomain(), "domain_tenant");
    }
}
