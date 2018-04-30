package jetbrains.buildServer.clouds.openstack;

public class OpenstackIdentity {

    private String credentials;
    private String tenant;
    private String tenantDomain;

    /**
     * OpenStack v2/v3 keystone JClouds <-> Specs API (cf. https://issues.apache.org/jira/browse/JCLOUDS-1414)<br/>
     * v2 tenant:user = 'tenant:user' in credentials, empty 'tenant' and 'tenantDomain'<br/>
     * v3 tenant:user = 'user' in credentials, 'tenant' as tenant<br/>
     * v3 tenant:domain_user:user = 'domain_user:user' in credentials, as tenant<br/>
     * v3 domainTenant:tenant:domainUser:user = 'domainUser:user' in creds, others properties in correct field<br/>
     * 
     * @param identity String
     * @param keyStoneVersion Object
     */
    public OpenstackIdentity(String identity, String keyStoneVersion) {
        if (identity == null) {
            return;
        }
        String[] array = identity.split(":");
        if (array.length == 0) {
            this.credentials = "";
        } else if (array.length == 1) {
            this.credentials = array[0];
        } else if (array.length == 2) {
            if ("2".equals(keyStoneVersion)) {
                this.credentials = identity;
            } else {
                this.credentials = array[1];
                this.tenant = array[0];
            }
        } else if (array.length == 3) {
            this.credentials = array[1] + ":" + array[2];
            if (!"2".equals(keyStoneVersion)) {
                this.tenant = array[0];
            }
        } else {
            this.credentials = array[array.length - 2] + ":" + array[array.length - 1];
            if (!"2".equals(keyStoneVersion)) {
                this.tenant = array[array.length - 3];
                this.tenantDomain = array[array.length - 4];
            }
        }
    }

    /**
     * For Openstack v2: 'tenant:user'<br/>
     * For Openstack v3: '[domain_user:]user'
     * 
     * @return Credentials for OPenstack keystone
     */
    public String getCredendials() {
        return this.credentials;
    }

    /**
     * For Openstack v2: empty (tenant is part of credentials)<br/>
     * For Openstack v3: tenant
     * 
     * @return Tenant
     */
    public String getTenant() {
        return tenant;
    }

    /**
     * For Openstack v3 only
     * 
     * @return domain_tenant
     */
    public String getTenantDomain() {
        return tenantDomain;
    }

}
