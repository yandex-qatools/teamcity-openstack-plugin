[![Build Status](https://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityThirdPartyPlugins_OpenStackCloudSupport_BuildSnapshotIntegration/statusIcon)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityThirdPartyPlugins_OpenStackCloudSupport_BuildSnapshotIntegration) [![SonarCloud Status](https://sonarcloud.io/api/project_badges/measure?project=jetbrains.buildServer.clouds:cloud-openstack&metric=alert_status)](https://sonarcloud.io/dashboard?id=jetbrains.buildServer.clouds:cloud-openstack)

# TeamCity Cloud Openstack plugin

## Download

(Currently only SNAPSHOT, v1.0 will coming soon).

| Download | Compatibility |
|----------|---------------|
| [Download](https://teamcity.jetbrains.com/repository/download/TeamCityThirdPartyPlugins_OpenStackCloudSupport_BuildSnapshotIntegration/.lastSuccessful/cloud-openstack.zip?guest=1) | TeamCity 10+ |

## Agent Configuration

1. create one or more Openstack machines
2. install a standard TeamCity build agent on them, you only need to fill TEAMCITY_SERVER_URL
WARNING: you shouldn't start build agent while preparing image
3. create images from machines with installed agent.

## Server Configuration

Fill cloud config with your openstack-instance parameters.
Configuration example:
<dl>
  <img src=http://img-fotki.yandex.ru/get/6805/95491511.0/0_111539_69e1c98b_XXL border=1></img>
</dl>

Once you have created a cloud profile in TeamCity with one or several images, TeamCity does a test start for all the new images to discover the environment of the build agents configured on them. 
If for a queued build there are no regular non-cloud agents available, TeamCity will find a matching cloud image with a compatible agent and start a new instance for the image. After that, a virtual agent acts as a regular agent.
You can specify idle time on the agent cloud profile, after which the instance should be terminated or stopped, in case you have an EBS-based instance.

### Agent images YAML parameters

| **Property**        | **Required** | **Description** |
|---------------------|--------------|-----------------|
| *image*             | true         | [Image](https://docs.openstack.org/glance/latest/admin/manage-images.html), ex: `ubuntu_16.04` |
| *flavor*            | true         | [Flavor](https://docs.openstack.org/horizon/latest/admin/manage-flavors.html), ex: `m1.medium` |
| *network*           | true         | [Network](https://developer.openstack.org/api-ref/network/v2/index.html#general-api-overview), ex: `VLAN` |
| *security_group*    | true         | [Security group](https://docs.openstack.org/nova/latest/admin/security-groups.html), ex: `default` |
| *key_pair*          | false        | [Key pair](https://docs.openstack.org/horizon/latest/user/configure-access-and-security-for-instances.html), ex: `my-key` ; required for SSH connection on created instances (like TeamCity Agent Push feature) |
| *auto_floating_ip*  | false        | Boolean (`false` by default) for [floating ip](https://docs.openstack.org/ocata/user-guide/cli-manage-ip-addresses.html) association ; first from pool used |
| *user_script*       | false        | Script executed on instance start |
| *availability_zone* | false        | Region for server instance (if not the global configured)

### OpenStack v2 Identity

The *Identity* defines the tenant/project and username, like: `tenant:user`

### OpenStack v3 Identity

The *Identity* defines at minimum the *tenant* and *user* informations, but could in addition defines the *domain(s)* of each items. In this case, only [project-scope](https://docs.openstack.org/keystone/queens/api_curl_examples.html#project-scoped) is supported.

The Identity is a 2-4 blocks string in this order: `[domain_tenant:]tenant:[domain_user:]user` (Warning: Priority given to *domain_user* for a 3 blocks strings).

#### Samples

Below some samples from *Identity* field to JSon produced on https://openstack.hostname.com/v3/auth/tokens URL.

##### myTenant:foo

```
{"auth":{"identity":{"methods":["password"],"password":{"user":{"name":"foo","domain":{},"password":"***"}}},"scope":{"project":{"name":"myTenant","domain":{}}}}}
```

##### myTenant:ldap:foo 

NB: *domain_user* is used for both domains.

```
{"auth":{"identity":{"methods":["password"],"password":{"user":{"name":"foo","domain":{"name":"ldap"},"password":"***"}}},"scope":{"project":{"name":"myTenant","domain":{"name":"ldap"}}}}}
```

##### myTenantDomain:myTenant:ldap:foo 

```
{"auth":{"identity":{"methods":["password"],"password":{"user":{"name":"foo","domain":{"name":"ldap"},"password":"***"}}},"scope":{"project":{"name":"myTenant","domain":{"id":"myTenantDomain"}}}}}
```

## Usage

Use Openstack virtual agents as regular build agents


### Metadata disable

With this plugin, any TeamCity agent on an Openstack virtual machine retrieves its information from `http://169.254.169.254/openstack/latest/meta_data.json` (uuid, name, user datas).

If you want disable this metadata usage, please add in agent configuration file (`buildAgent.properties`):

```
clouds.openstack.metadata.disable = true
```

This usage is mainly designed for instantiate some TeamCity agent(s) on an Openstack virtual machine as a classic way (name defined in configuration file, ...), without they are in cloud profile.

## Build and Tests

1. clone current repository to your local computer

2. Provides 4 test files in *server* classpath (ex: `cloud-openstack-server/src/test/resources`) with content:

```
# File: test.v3.properties
test.url=https://openstack.company.com/v3
test.identity=domain_tenant:tenant:domain_user:user
test.password=foobar
test.region=region1
```

```
# File: test.v3.yml
openstack-test-teamcity-plugin:
  image: anyImage
  flavor: m1.small
  network: networkProviderName
  security_group: default
  key_pair: yourKey
```

```
# File: test.v2.properties
test.url=https://openstack.company.com/v2.0
test.identity=tenant:user
test.password=foobar
test.region=region1
```

```
# File: test.v2.yml
openstack-test-teamcity-plugin:
  image: anyImage
  flavor: m1.small
  network: networkProviderName
  security_group: default
  key_pair: yourKey
```  

3. run `mvn clean package` (if OpenStack test endpoint requires trustStore certificate not in JVM used for test, add `-Djavax.net.ssl.trustStore=/path/to/cacerts`)

4. install resulted *cloud-openstack.zip* plugin file to TeamCity server
