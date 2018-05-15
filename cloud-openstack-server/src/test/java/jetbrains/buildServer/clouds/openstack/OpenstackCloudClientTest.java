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
                waitInstanceStatus(instance, InstanceStatus.STOPPED, 5000, InstanceStatus.RUNNING, InstanceStatus.SCHEDULED_TO_STOP,
                        InstanceStatus.STOPPING);
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
