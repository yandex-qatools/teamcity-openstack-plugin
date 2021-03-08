package jetbrains.buildServer.clouds.openstack;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.clouds.openstack.util.Lo4jBeanAppender;
import jetbrains.buildServer.util.EventDispatcher;

public class OpenstackAgentPropertiesTest {

    private WireMockServer wireMockServer;

    @BeforeMethod
    public void setUp() {
        // Initialize manually, WireMockRule requires JUnit
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor(wireMockServer.port());
        Lo4jBeanAppender.clear();
    }

    @AfterMethod
    public void tearDown() {
        wireMockServer.stop();
    }

    private OpenstackAgentProperties prepareAgentProperties() throws IOException {
        return prepareAgentProperties(null, null);
    }

    private OpenstackAgentProperties prepareAgentProperties(String jsonResponseContentFileInTestResources) throws IOException {
        return prepareAgentProperties(jsonResponseContentFileInTestResources, null);
    }

    private OpenstackAgentProperties prepareAgentProperties(String jsonResponseContentFileInTestResources, Map<String, String> parameters)
            throws IOException {
        return prepareAgentProperties(jsonResponseContentFileInTestResources, parameters, 200);
    }

    private OpenstackAgentProperties prepareAgentProperties(String jsonResponseContentFileInTestResources, Map<String, String> parameters,
            int httpCodeReturn) throws IOException {

        final Mockery context = new Mockery();
        final BuildAgentConfigurationEx agentConfig = context.mock(BuildAgentConfigurationEx.class);
        context.checking(new Expectations() {
            {
                allowing(agentConfig).addConfigurationParameter(with(any(String.class)), with(any(String.class)));
                allowing(agentConfig).setName(with(any(String.class)));
                allowing(agentConfig).addAlternativeAgentAddress(with(any(String.class)));
                allowing(agentConfig).getConfigurationParameters();
                if (parameters != null && !parameters.isEmpty()) {
                    will(returnValue(parameters));
                }
            }
        });

        context.setImposteriser(ClassImposteriser.INSTANCE);
        @SuppressWarnings("unchecked")
        EventDispatcher<AgentLifeCycleListener> dispatcher = context.mock(EventDispatcher.class);
        context.checking(new Expectations() {
            {
                allowing(dispatcher).addListener(with(any(OpenstackAgentProperties.class)));
            }
        });
        final OpenstackAgentProperties oap = new OpenstackAgentProperties(agentConfig, dispatcher);

        if (StringUtils.isNoneBlank(jsonResponseContentFileInTestResources)) {
            final String endpoint = "/openstack/test/meta_data.json";
            stubFor(get(urlEqualTo(endpoint)).willReturn(aResponse().withStatus(httpCodeReturn).withHeader("Content-Type", "application/json")
                    .withBody(FileUtils.readFileToString(new File("src/test/resources", jsonResponseContentFileInTestResources)))));
            oap.setUrlMetaData("http://localhost:" + wireMockServer.port() + endpoint);
        }
        return oap;
    }

    @Test
    public void testRealNetWorkMetaData() throws IOException {
        // Leave real Openstack URL
        OpenstackAgentProperties props = prepareAgentProperties();
        props.afterAgentConfigurationLoaded(null);

        URL url = new URL(props.getMetadataUrl());
        boolean openstackInstance = false;
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int code = connection.getResponseCode();
            if (HttpStatus.SC_OK == code) {
                openstackInstance = true;
            }
        } catch (IOException e) {
            // No open stack instance
        }

        if (openstackInstance) {
            Assert.assertTrue(Lo4jBeanAppender.contains("Detected Openstack instance. Will write parameters from metadata"));
        } else {
            Assert.assertTrue(Lo4jBeanAppender.contains("It seems build-agent launched at non-Openstack instance"));
        }
    }

    @Test
    public void testNoUserData() throws IOException {
        prepareAgentProperties("meta_data_noUserData.json").afterAgentConfigurationLoaded(null);

        Assert.assertTrue(Lo4jBeanAppender.contains("Detected Openstack instance. Will write parameters from metadata"));
        Assert.assertTrue(Lo4jBeanAppender.contains("Setting agent.cloud.uuid to xxxx-yyyyy-zzzz"));
        Assert.assertTrue(Lo4jBeanAppender.contains("Setting name to openstack_test"));

        Assert.assertFalse(Lo4jBeanAppender.contains("It seems build-agent launched at non-Openstack instance"));
        Assert.assertFalse(Lo4jBeanAppender.contains("Network is unreachable"));
        Assert.assertFalse(Lo4jBeanAppender.contains("Unknow problem on Openstack plugin"));
    }

    @Test
    public void testUserData() throws IOException {
        prepareAgentProperties("meta_data_userData.json").afterAgentConfigurationLoaded(null);

        Assert.assertTrue(Lo4jBeanAppender.contains("Detected Openstack instance. Will write parameters from metadata"));
        Assert.assertTrue(Lo4jBeanAppender.contains("Setting agent.cloud.ip to 192.168.42.42"));

        Assert.assertFalse(Lo4jBeanAppender.contains("It seems build-agent launched at non-Openstack instance"));
        Assert.assertFalse(Lo4jBeanAppender.contains("Network is unreachable"));
        Assert.assertFalse(Lo4jBeanAppender.contains("Unknow problem on Openstack plugin"));
    }

    @Test
    public void testJsonTruncated() throws IOException {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(OpenstackCloudParameters.AGENT_METADATA_DISABLE, "false");
        prepareAgentProperties("meta_data_truncated.json", parameters).afterAgentConfigurationLoaded(null);
        Assert.assertTrue(Lo4jBeanAppender.contains("Detected Openstack instance. Will write parameters from metadata"));
        Assert.assertTrue(Lo4jBeanAppender.contains("Unknow problem on Openstack plugin"));
    }

    @Test
    public void testMetaDataUsageDisable() throws IOException {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(OpenstackCloudParameters.AGENT_METADATA_DISABLE, "true");
        prepareAgentProperties("meta_data_noUserData.json", parameters).afterAgentConfigurationLoaded(null);
        Assert.assertTrue(Lo4jBeanAppender.contains("Openstack metadata usage disabled (agent configuration not overridden)"));
        Assert.assertFalse(Lo4jBeanAppender.contains("Detected Openstack instance. Will write parameters from metadata"));
        Assert.assertFalse(Lo4jBeanAppender.contains("Unknow problem on Openstack plugin"));
    }

    @Test
    public void testProxyError() throws IOException {
        prepareAgentProperties("meta_data_userData.json", null, 407).afterAgentConfigurationLoaded(null);

        Assert.assertTrue(Lo4jBeanAppender.contains("It seems build-agent launched at non-Openstack instance"));
        Assert.assertFalse(Lo4jBeanAppender.contains("Network is unreachable"));
        Assert.assertFalse(Lo4jBeanAppender.contains("Unknow problem on Openstack plugin"));
    }
}
