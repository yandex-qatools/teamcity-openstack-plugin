# TeamCity Cloud Openstack plugin

## Installation
1. clone current repository to your local computer
2. Provides two test files in *server* classpath (ex: `cloud-openstack-server/src/test/resources`) with content:

```
# File: test.properties
test.v2.url=https://openstack.company.com/v2.0
test.v2.identity=tenant:user
test.v2.password=foobar
test.v2.region=region1
test.v3.url=https://openstack.company.com/v3
test.v3.identity=domain:tenant:user
test.v3.password=foobar
test.v3.region=region1
```

```
# File: test.yml
openstack-test-teamcity-plugin:
  image: anyImage
  flavor: m1.small
  network: networkProviderName
  security_group: default
  key_pair: yourKey
```  

3. run `mvn clean package` (if OpenStack test endpoint requires trustStore certificate not in JVM used for test, add `-Djavax.net.ssl.trustStore=/path/to/cacerts`)
4. install resulted *.zip file to teamcity server

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

**Note**: The *identity* format for OpenStack v3 is `domain:tenant:user`. If *domain* not filled, the `default` value will be used.  

## Usage
Use Openstack virtual agents as regular build agents
