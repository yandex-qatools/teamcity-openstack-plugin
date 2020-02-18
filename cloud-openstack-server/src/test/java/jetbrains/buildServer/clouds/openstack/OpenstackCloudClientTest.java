package jetbrains.buildServer.clouds.openstack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.intellij.openapi.util.text.StringUtil;

import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;

public class OpenstackCloudClientTest extends AbstractTestOpenstackCloudClient {

    final private static String TEST_KEY_URL = "test.url";
    final private static String TEST_KEY_IDENTITY = "test.identity";
    final private static String TEST_KEY_PASSWORD = "test.password";
    final private static String TEST_KEY_REGION = "test.region";

    final private static String[] TEST_KEYS_LIST = new String[] { TEST_KEY_URL, TEST_KEY_IDENTITY, TEST_KEY_PASSWORD, TEST_KEY_REGION, };

    static enum OpenStackVersion {
        TWO("2"), THREE("3");

        private final String value;

        private OpenStackVersion(String value) {
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
                props.getProperty(TEST_KEY_REGION), getTestYaml(OpenStackVersion.TWO.value));
    }

    @Test
    public void testV3() throws Exception {
        Properties props = getTestProps(OpenStackVersion.THREE);
        testSubSimple(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY), props.getProperty(TEST_KEY_PASSWORD),
                props.getProperty(TEST_KEY_REGION), getTestYaml(OpenStackVersion.THREE.value));
    }

    @Test
    public void testWithUserScript() throws Exception {
        // Test data should not include floating ip (instance more longer to create)
        final String scriptName = "fakeUserScript.sh";
        final File fakeUserScript = new File("target/system/pluginData/openstack", scriptName);
        FileUtils.writeStringToFile(fakeUserScript, "echo foo bar");

        Properties props = getTestProps(OpenStackVersion.TWO);
        testSubSimple(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY), props.getProperty(TEST_KEY_PASSWORD),
                props.getProperty(TEST_KEY_REGION), getTestYaml(OpenStackVersion.TWO.value) + "\n  user_script: " + scriptName);
    }

    @Test
    public void testWithUserScriptNotExist() throws Exception {
        // Test data should not include floating ip (instance more longer to create)
        Properties props = getTestProps(OpenStackVersion.TWO);
        String errorMsg = testSubSimple(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY), props.getProperty(TEST_KEY_PASSWORD),
                props.getProperty(TEST_KEY_REGION), getTestYaml(OpenStackVersion.TWO.value) + "\n  user_script: fakeScriptNotExist-87648348376.sh",
                true, false);
        Assert.assertTrue(errorMsg.contains("Error in reading user script"), errorMsg);
    }

    @Test
    public void testWithBadImageName() throws Exception {
        Properties props = getTestProps(OpenStackVersion.TWO);
        String yaml = getTestYaml(OpenStackVersion.TWO.value);
        yaml = yaml.replaceFirst("image: .*\n", "image: imageNotExist4242\n");
        String errorMsg = testSubSimple(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY), props.getProperty(TEST_KEY_PASSWORD),
                props.getProperty(TEST_KEY_REGION), yaml, true, false);
        Assert.assertTrue(errorMsg.contains("No image can be found for name"), errorMsg);
    }

    @Test
    public void testFindImageById() throws Exception {
        Properties props = getTestProps(OpenStackVersion.THREE);
        OpenstackCloudClient client = getClient(props.getProperty(TEST_KEY_URL), props.getProperty(TEST_KEY_IDENTITY),
                props.getProperty(TEST_KEY_PASSWORD), props.getProperty(TEST_KEY_REGION), getTestYaml(OpenStackVersion.THREE.value));

        for (OpenstackCloudImage image : client.getImages()) {
            OpenstackCloudImage newImage = client.findImageById(image.getId());
            Assert.assertNotNull(newImage);
            Assert.assertEquals(image.getName(), newImage.getName());
        }

        client.dispose();
    }

    @Test
    public void testRestoration() throws Exception {
        Properties props = getTestProps(OpenStackVersion.THREE);
        String endpointUrl = props.getProperty(TEST_KEY_URL);
        String identity = props.getProperty(TEST_KEY_IDENTITY);
        String password = props.getProperty(TEST_KEY_PASSWORD);
        String region = props.getProperty(TEST_KEY_REGION);
        String yaml = getTestYaml(OpenStackVersion.THREE.value);

        // Start first with 1 VM
        OpenstackCloudClient client = getClient(endpointUrl, identity, password, region, yaml);
        CloudInstance instance = client.startNewInstance(client.getImages().iterator().next(),
                new CloudInstanceUserData("fakeName", "fakeToken", "localhost", (long) 0, "", "", new HashMap<>()));
        waitInstanceStatus(instance, InstanceStatus.RUNNING, 5000,
                new ArrayList<>(Arrays.asList(InstanceStatus.SCHEDULED_TO_START, InstanceStatus.STARTING)));
        while (!client.isInitialized()) {
            // Wait client initialization
            Thread.sleep(1000); // NOSONAR : Wanted for unit test
        }
        Assert.assertEquals(client.getImages().iterator().next().getInstances().size(), 1);

        // Simulate an update
        client.dispose();

        // Recreate without any VM start
        client = getClient(endpointUrl, identity, password, region, yaml);
        while (!client.isInitialized()) {
            // Wait client initialization
            Thread.sleep(1000); // NOSONAR : Wanted for unit test
        }
        // Waiting async restoration execution
        Thread.sleep(3000); // NOSONAR : Wanted for unit test

        // Assert correct restoration
        Assert.assertEquals(client.getImages().iterator().next().getInstances().size(), 1);

        // Clean all
        instance = client.getImages().iterator().next().getInstances().iterator().next();
        client.terminateInstance(instance);
        waitInstanceStatus(instance, InstanceStatus.STOPPED, 5000, new ArrayList<>(
                Arrays.asList(InstanceStatus.RUNNING, InstanceStatus.SCHEDULED_TO_STOP, InstanceStatus.STOPPING, InstanceStatus.STOPPED)));
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

}
