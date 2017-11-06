# TeamCity Cloud Openstack plugin

## Installation
1. clone current repository to your local computer
2. run "mvn clean package"
3. install resulted *.zip file to teamcity server

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

## Usage
Use Openstack virtual agents as regular build agents


### Metadata disable

With this plugin, any TeamCity agent on an Openstack virtual machine retrieves its information from `http://169.254.169.254/openstack/latest/meta_data.json` (uuid, name, user datas).

If you want disable this metadata usage, please add in agent configuration file (`buildAgent.properties`):

```
clouds.openstack.metadata.disable = true
```

This usage is mainly designed for instantiate some TeamCity agent(s) on an Openstack virtual machine as a classic way (name defined in configuration file, ...), without they are in cloud profile.