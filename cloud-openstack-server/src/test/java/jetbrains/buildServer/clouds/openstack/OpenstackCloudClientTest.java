package jetbrains.buildServer.clouds.openstack;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
import jetbrains.buildServer.web.openapi.PluginDescriptor;

public class OpenstackCloudClientTest {

    final private static String TEST_KEY_V2_URL = "test.v2.url";
    final private static String TEST_KEY_V2_IDENTITY = "test.v2.identity";
    final private static String TEST_KEY_V2_PASSWORD = "test.v2.password";
    final private static String TEST_KEY_V2_REGION = "test.v2.region";
    final private static String TEST_KEY_V3_URL = "test.v3.url";
    final private static String TEST_KEY_V3_IDENTITY = "test.v3.identity";
    final private static String TEST_KEY_V3_PASSWORD = "test.v3.password";
    final private static String TEST_KEY_V3_REGION = "test.v3.region";

    final private static String TEST_FILE_PROPERTIES = "/test.properties";
    final private static String TEST_FILE_YAML = "/test.yml";
    final private static String[] TEST_KEYS_LIST = new String[] { TEST_KEY_V2_URL, TEST_KEY_V2_IDENTITY, TEST_KEY_V2_PASSWORD, TEST_KEY_V2_REGION,
            TEST_KEY_V3_URL, TEST_KEY_V3_IDENTITY, TEST_KEY_V3_PASSWORD, TEST_KEY_V3_REGION };

    @Test
    public void testNoImage() throws Exception {
        Properties props = getTestProps();
        OpenstackCloudClient client = getClient(props.getProperty(TEST_KEY_V2_URL), props.getProperty(TEST_KEY_V2_IDENTITY),
                props.getProperty(TEST_KEY_V2_PASSWORD), props.getProperty(TEST_KEY_V2_REGION), null);
        Assert.assertEquals("No images specified", client.getErrorInfo().getMessage());
    }

    @Test
    public void testV2() throws Exception {
        Properties props = getTestProps();
        testSubSimple(props.getProperty(TEST_KEY_V2_URL), props.getProperty(TEST_KEY_V2_IDENTITY), props.getProperty(TEST_KEY_V2_PASSWORD),
                props.getProperty(TEST_KEY_V2_REGION), getTestYaml());
    }

    @Test
    public void testV3() throws Exception {
        Properties props = getTestProps();
        testSubSimple(props.getProperty(TEST_KEY_V3_URL), props.getProperty(TEST_KEY_V3_IDENTITY), props.getProperty(TEST_KEY_V3_PASSWORD),
                props.getProperty(TEST_KEY_V3_REGION), getTestYaml());
    }

    private Properties getTestProps() throws IOException {
        Properties props = new Properties();
        InputStream is = this.getClass().getResourceAsStream(TEST_FILE_PROPERTIES);
        if (is == null) {
            throw new UnsupportedOperationException(String.format("You should provide a '%s' file in test resrources with keys: %s",
                    TEST_FILE_PROPERTIES, StringUtil.join(TEST_KEYS_LIST, " / ")));
        }
        props.load(is);
        return props;
    }

    private String getTestYaml() throws IOException {
        // Old 'commons-io', but provided by TeamCity 'server-api' ... this is just for UT
        InputStream is = this.getClass().getResourceAsStream(TEST_FILE_YAML);
        if (is == null) {
            throw new UnsupportedOperationException(
                    String.format("You should provide a '%s' file in test resrources containg OpenStack image descriptor", TEST_FILE_YAML));
        }
        @SuppressWarnings("unchecked")
        List<String> list = IOUtils.readLines(is);
        return StringUtil.join(list, "\n");
    }

    private void testSubSimple(String endpointUrl, String identity, String password, String region, String yaml) throws Exception {
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
            waitInstanceStatus(instance, InstanceStatus.RUNNING, 5000, InstanceStatus.SCHEDULED_TO_START, InstanceStatus.STARTING);
        } finally {
            if (instance != null) {
                client.terminateInstance(instance);
                waitInstanceStatus(instance, InstanceStatus.STOPPED, 5000, InstanceStatus.SCHEDULED_TO_STOP, InstanceStatus.STOPPING);
            }
        }
    }

    private void waitInstanceStatus(CloudInstance instance, InstanceStatus wanted, long intervalWait, InstanceStatus... intermediates)
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

        OpenstackCloudClientFactory factory = new OpenstackCloudClientFactory(cloudRegistrar, pluginDescriptor, null);
        return factory.createNewClient(null, new TestCloudClientParameters(params));
    }

}
