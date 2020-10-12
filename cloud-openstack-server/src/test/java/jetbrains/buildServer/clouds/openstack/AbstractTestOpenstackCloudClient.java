package jetbrains.buildServer.clouds.openstack;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.Assert;

import com.intellij.openapi.util.text.StringUtil;

import jetbrains.buildServer.clouds.CanStartNewInstanceResult;
import jetbrains.buildServer.clouds.CloudClientFactory;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.CloudRegistrar;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.openstack.util.TestCloudClientParameters;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.web.openapi.PluginDescriptor;

public class AbstractTestOpenstackCloudClient {

    protected OpenstackCloudClient getClient(String endpointUrl, String identity, String password, String region, String yaml) {
        Map<String, String> params = new HashMap<>();
        params.put(OpenstackCloudParameters.ENDPOINT_URL, endpointUrl);
        params.put(OpenstackCloudParameters.IDENTITY, identity);
        params.put(OpenstackCloudParameters.PASSWORD, password);
        params.put(OpenstackCloudParameters.REGION, region);
        params.put(OpenstackCloudParameters.IMAGES_PROFILES, yaml);
        params.put(OpenstackCloudParameters.INSTANCE_CAP, "1");

        final Mockery context = new Mockery();

        final PluginDescriptor pluginDescriptor = context.mock(PluginDescriptor.class);
        context.checking(new Expectations() {
            {
                oneOf(pluginDescriptor).getPluginResourcesPath("profile-settings.jsp");
                will(returnValue("target/fake"));
            }
        });

        final CloudRegistrar cloudRegistrar = context.mock(CloudRegistrar.class);
        context.checking(new Expectations() {
            {
                oneOf(cloudRegistrar).registerCloudFactory(with(aNonNull(CloudClientFactory.class)));
            }
        });

        OpenstackCloudClientFactory factory = new OpenstackCloudClientFactory(cloudRegistrar, pluginDescriptor, new ServerPaths("target"));
        return factory.createNewClient(null, new TestCloudClientParameters(params));
    }

    protected String getTestYaml(String version) throws IOException {
        final String file = "test.v" + version + ".yml";

        // Old 'commons-io', but provided by TeamCity 'server-api' ... this is just for unit test
        InputStream is = this.getClass().getResourceAsStream("/" + file);
        if (is == null) {
            throw new UnsupportedOperationException(
                    String.format("You should provide a '%s' file in test resrources containg OpenStack image descriptor", file));
        }
        @SuppressWarnings("unchecked")
        List<String> list = IOUtils.readLines(is);
        return StringUtil.join(list, "\n");
    }

    protected void testSubSimple(String endpointUrl, String identity, String password, String region, String yaml) throws Exception {
        String errorMsg = testSubSimple(endpointUrl, identity, password, region, yaml, false, false);
        Assert.assertNull(errorMsg);
    }

    /**
     * Spy some method of CloudImage (nothing by default)
     * 
     * @param image Image to spy
     * @return New image
     */
    protected CloudImage spyCloudImage(CloudImage image) {
        return image;
    }

    protected String testSubSimple(String endpointUrl, String identity, String password, String region, String yaml,
            boolean errorInstanceWillOccursAtStart, boolean errorInstanceWillOccursAtEnd) throws Exception {
        String returnMessage = null;
        Date startTime = new Date(System.currentTimeMillis() - 1000);
        OpenstackCloudClient client = getClient(endpointUrl, identity, password, region, yaml);
        Assert.assertNull(client.getErrorInfo());
        Assert.assertNotNull(client.getImages());
        Assert.assertFalse(client.getImages().isEmpty());
        CloudImage image = spyCloudImage(client.getImages().iterator().next());

        Assert.assertEquals(client.canStartNewInstanceWithDetails(image), CanStartNewInstanceResult.yes());
        CloudInstance instance = null;
        try {
            instance = client.startNewInstance(image,
                    new CloudInstanceUserData("fakeName", "fakeToken", "localhost", (long) 0, "", "", new HashMap<>()));
            List<InstanceStatus> statusInit = new ArrayList<>(Arrays.asList(InstanceStatus.SCHEDULED_TO_START, InstanceStatus.STARTING));
            InstanceStatus statusWanted = InstanceStatus.RUNNING;
            if (errorInstanceWillOccursAtStart) {
                statusWanted = InstanceStatus.ERROR;
                statusInit.add(InstanceStatus.ERROR);
                statusInit.add(InstanceStatus.STOPPED);
            }
            waitInstanceStatus(instance, statusWanted, 5000, statusInit);
            if (errorInstanceWillOccursAtStart) {
                return instance.getErrorInfo().getMessage();
            }
            String instanceId = ((OpenstackCloudInstance) instance).getOpenstackInstanceId();
            Assert.assertTrue(!StringUtil.isEmpty(instanceId));
            Assert.assertNotNull(instance.getImage());
            Assert.assertTrue(!StringUtil.isEmpty(instance.getImageId()));
            // No possible Assert for network identity, only v3 return non-null value
            instance.getNetworkIdentity();
            Assert.assertNotNull(instance.getStartedTime());
            Assert.assertTrue(instance.getStartedTime().after(startTime),
                    String.format("Begin: %s / InstanceStart: %s", startTime, instance.getStartedTime()));

            Map<String, String> parameters = new HashMap<>();
            AgentDescription agentDescription = mock(AgentDescription.class);
            when(agentDescription.getConfigurationParameters()).thenReturn(parameters);
            Assert.assertFalse(instance.containsAgent(agentDescription));
            parameters.put("testCloudType", OpenstackCloudParameters.CLOUD_TYPE);
            Assert.assertFalse(instance.containsAgent(agentDescription));
            parameters.put(OpenstackCloudParameters.OPENSTACK_INSTANCE_ID, instanceId);
            Assert.assertTrue(instance.containsAgent(agentDescription));
        } finally {
            if (instance != null) {
                client.terminateInstance(instance);
                List<InstanceStatus> statusTerminate = new ArrayList<>(
                        Arrays.asList(InstanceStatus.RUNNING, InstanceStatus.SCHEDULED_TO_STOP, InstanceStatus.STOPPING, InstanceStatus.STOPPED));
                InstanceStatus statusWanted = InstanceStatus.STOPPED;
                if (errorInstanceWillOccursAtStart || errorInstanceWillOccursAtEnd) {
                    statusWanted = InstanceStatus.ERROR;
                    statusTerminate.add(InstanceStatus.ERROR);
                }
                waitInstanceStatus(instance, statusWanted, 5000, statusTerminate);
                if (errorInstanceWillOccursAtEnd && instance.getErrorInfo() != null) {
                    returnMessage = instance.getErrorInfo().getMessage();
                }
            }
            client.dispose();
        }
        return returnMessage;
    }

    protected void waitInstanceStatus(CloudInstance instance, InstanceStatus wanted, long intervalWait, List<InstanceStatus> intermediates)
            throws InterruptedException {
        while (!wanted.equals(instance.getStatus())) {
            boolean currentIsInIntermediates = false;
            for (InstanceStatus intermediate : intermediates) {
                if (instance.getStatus().equals(intermediate)) {
                    currentIsInIntermediates = true;
                    break;
                }
            }
            if (!currentIsInIntermediates) {
                Assert.fail(String.format("Status '%s' is not one of intermediates expected", instance.getStatus().getName()));
            }
            Thread.sleep(intervalWait); // NOSONAR: Sleep wanted
        }
    }
}
