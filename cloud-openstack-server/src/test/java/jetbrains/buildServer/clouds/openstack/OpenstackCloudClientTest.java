package jetbrains.buildServer.clouds.openstack;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.intellij.openapi.util.text.StringUtil;

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

public class OpenstackCloudClientTest {

    final private static String TEST_KEY_URL = "test.url";
    final private static String TEST_KEY_IDENTITY = "test.identity";
    final private static String TEST_KEY_PASSWORD = "test.password";
    final private static String TEST_KEY_REGION = "test.region";

    final private static String[] TEST_KEYS_LIST = new String[] { TEST_KEY_URL, TEST_KEY_IDENTITY, TEST_KEY_PASSWORD, TEST_KEY_REGION, };

    static enum OpenStackVersion {
        TWO(2), THREE(3);

        private final int value;

        private OpenStackVersion(int value) {
            this.value = value;
        }
    };

    @Test
    public void testNoImage() throws Exception {
        Properties props = getTestProps(OpenStackVersion.TWO);
        OpenstackCloudClient client = getClient(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY),
                props.getProperty(TEST_KEY_PASSWORD), props.getProperty(TEST_KEY_REGION), null);
        Assert.assertEquals("No images specified", client.getErrorInfo().getMessage());
    }

    @Test
    public void testOnlyComments() throws Exception {
        Properties props = getTestProps(OpenStackVersion.TWO);
        OpenstackCloudClient client = getClient(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY),
                props.getProperty(TEST_KEY_PASSWORD), props.getProperty(TEST_KEY_REGION), "#A comment");
        Assert.assertEquals("No images specified (perhaps only comments)", client.getErrorInfo().getMessage());
    }

    @Test
    public void testNoParams() throws Exception {
        Properties props = getTestProps(OpenStackVersion.TWO);
        OpenstackCloudClient client = getClient(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY),
                props.getProperty(TEST_KEY_PASSWORD), props.getProperty(TEST_KEY_REGION), "some-image:");
        Assert.assertEquals("No parameters defined for image: some-image", client.getErrorInfo().getMessage());
    }

    @Test
    public void testV2() throws Exception {
        Properties props = getTestProps(OpenStackVersion.TWO);
        testSubSimple(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY), props.getProperty(TEST_KEY_PASSWORD),
                props.getProperty(TEST_KEY_REGION), getTestYaml(OpenStackVersion.TWO));
    }

    @Test
    public void testV3() throws Exception {
        Properties props = getTestProps(OpenStackVersion.THREE);
        testSubSimple(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY), props.getProperty(TEST_KEY_PASSWORD),
                props.getProperty(TEST_KEY_REGION), getTestYaml(OpenStackVersion.THREE));
    }

    @Test
    public void testWithUserScript() throws Exception {
        // Test data should not include floating ip (instance more longer to create)
        final String scriptName = "fakeUserScript.sh";
        final File fakeUserScript = new File("target/system/pluginData/openstack", scriptName);
        FileUtils.writeStringToFile(fakeUserScript, "echo foo bar");

        Properties props = getTestProps(OpenStackVersion.TWO);
        testSubSimple(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY), props.getProperty(TEST_KEY_PASSWORD),
                props.getProperty(TEST_KEY_REGION), getTestYaml(OpenStackVersion.TWO) + "\n  user_script: " + scriptName);
    }

    @Test
    public void testWithUserScriptNotExist() throws Exception {
        // Test data should not include floating ip (instance more longer to create)
        Properties props = getTestProps(OpenStackVersion.TWO);
        String errorMsg = testSubSimple(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY), props.getProperty(TEST_KEY_PASSWORD),
                props.getProperty(TEST_KEY_REGION), getTestYaml(OpenStackVersion.TWO) + "\n  user_script: fakeScriptNotExist-87648348376.sh", true);
        Assert.assertTrue(errorMsg.contains("Error in reading user script"), errorMsg);
    }

    @Test
    public void testWithBadImageName() throws Exception {
        Properties props = getTestProps(OpenStackVersion.TWO);
        String yaml = getTestYaml(OpenStackVersion.TWO);
        yaml = yaml.replaceFirst("image: .*\n", "image: imageNotExist4242\n");
        String errorMsg = testSubSimple(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY), props.getProperty(TEST_KEY_PASSWORD),
                props.getProperty(TEST_KEY_REGION), yaml, true);
        Assert.assertTrue(errorMsg.contains("No image can be found for name"), errorMsg);
    }

    @Test
    public void testFindImageById() throws Exception {
        Properties props = getTestProps(OpenStackVersion.THREE);
        OpenstackCloudClient client = getClient(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY),
                props.getProperty(TEST_KEY_PASSWORD), props.getProperty(TEST_KEY_REGION), getTestYaml(OpenStackVersion.THREE));

        for (OpenstackCloudImage image : client.getImages()) {
            OpenstackCloudImage newImage = client.findImageById(image.getId());
            Assert.assertNotNull(newImage);
            Assert.assertEquals(image.getName(), newImage.getName());
        }

        client.dispose();
    }

    private Properties getTestProps(OpenStackVersion version) throws IOException {
        final String file = "test.v" + version.value + ".properties";

        Properties props = new Properties();
        InputStream is = this.getClass().getResourceAsStream("/" + file);
        if (is == null) {
            throw new UnsupportedOperationException(
                    String.format("You should provide a '%s' file in test resrources with keys: %s", file, StringUtil.join(TEST_KEYS_LIST, " / ")));
        }
        props.load(is);
        return props;
    }

    private String getTestYaml(OpenStackVersion version) throws IOException {
        final String file = "test.v" + version.value + ".yml";

        // Old 'commons-io', but provided by TeamCity 'server-api' ... this is just for UT
        InputStream is = this.getClass().getResourceAsStream("/" + file);
        if (is == null) {
            throw new UnsupportedOperationException(
                    String.format("You should provide a '%s' file in test resrources containg OpenStack image descriptor", file));
        }
        @SuppressWarnings("unchecked")
        List<String> list = IOUtils.readLines(is);
        return StringUtil.join(list, "\n");
    }

    private void testSubSimple(String endpointUrl, String identity, String password, String region, String yaml) throws Exception {
        String errorMsg = testSubSimple(endpointUrl, identity, password, region, yaml, false);
        Assert.assertNull(errorMsg);
    }

    private String testSubSimple(String endpointUrl, String identity, String password, String region, String yaml, boolean errorInstanceWillOccurs)
            throws Exception {
        Date startTime = new Date(System.currentTimeMillis() - 1000);
        OpenstackCloudClient client = getClient(endpointUrl, identity, password, region, yaml);
        Assert.assertNull(client.getErrorInfo());
        Assert.assertNotNull(client.getImages());
        Assert.assertFalse(client.getImages().isEmpty());
        CloudImage image = client.getImages().iterator().next();
        Assert.assertTrue(client.canStartNewInstance(image));
        CloudInstance instance = null;
        try {
            instance = client.startNewInstance(image,
                    new CloudInstanceUserData("fakeName", "fakeToken", "localhost", (long) 0, "", "", new HashMap<>()));
            List<InstanceStatus> statusInit = new ArrayList<>(Arrays.asList(InstanceStatus.SCHEDULED_TO_START, InstanceStatus.STARTING));
            InstanceStatus statusWanted = InstanceStatus.RUNNING;
            if (errorInstanceWillOccurs) {
                statusWanted = InstanceStatus.ERROR;
                statusInit.add(InstanceStatus.ERROR);
            }
            waitInstanceStatus(instance, statusWanted, 5000, statusInit);
            if (errorInstanceWillOccurs) {
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

            return null;
        } finally {
            if (instance != null) {
                client.terminateInstance(instance);
                List<InstanceStatus> statusTerminate = new ArrayList<>(
                        Arrays.asList(InstanceStatus.RUNNING, InstanceStatus.SCHEDULED_TO_STOP, InstanceStatus.STOPPING));
                InstanceStatus statusWanted = InstanceStatus.STOPPED;
                if (errorInstanceWillOccurs) {
                    statusWanted = InstanceStatus.ERROR;
                    statusTerminate.add(InstanceStatus.ERROR);
                }
                waitInstanceStatus(instance, statusWanted, 5000, statusTerminate);
            }
        }
    }

    private void waitInstanceStatus(CloudInstance instance, InstanceStatus wanted, long intervalWait, List<InstanceStatus> intermediates)
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

    private OpenstackCloudClient getClient(String endpointUrl, String identity, String password, String region, String yaml) {
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

}
