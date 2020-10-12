package jetbrains.buildServer.clouds.openstack;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.openstack.util.Lo4jBeanAppender;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.TeamCityProperties.Model;
import jetbrains.buildServer.serverSide.TeamCityPropertiesMock;

public class OpenstackCloudClientMockedTest extends AbstractTestOpenstackCloudClient {

    private static final String SCENARIO = "server scenario";
    private static final String SCENARIO_STATE_INIT = "init";
    private static final String SCENARIO_STATE_RUN = "run";

    private WireMockServer wireMockServer;

    @BeforeMethod
    public void setUp() throws Exception {
        // Initialise manually, WireMockRule requires JUnit
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor(wireMockServer.port());
        Lo4jBeanAppender.clear();

        Model model = TeamCityProperties.getModel();
        System.out.println(model);

        // Accelerate a little the Mocked unit test
        TeamCityPropertiesMock.addProperty(OpenstackCloudImage.DELAY_STATUS_INITIAL_KEY, "3");
        TeamCityPropertiesMock.addProperty(OpenstackCloudImage.DELAY_STATUS_DELAY_KEY, "3");
    }

    @AfterMethod
    public void tearDown() {
        wireMockServer.stop();
        TeamCityPropertiesMock.reset();
    }

    private String getJSonTestAsStringWithReplaces(String fileName, String... replaces) throws IOException {
        String content = FileUtils.readFileToString(new File("src/test/resources/__files", fileName));
        final String p = "REPLACE-";
        if (!content.contains(p)) {
            throw new IOException(String.format("File '%s' does not contain any '%s' value", fileName, p));
        }
        if (replaces == null || replaces.length % 2 != 0) {
            throw new IOException("Replacement values are null/empty or not div 2");
        }
        for (int i = 0; i < replaces.length; i = i + 2) {
            content = content.replaceAll(replaces[i], replaces[i + 1]);
        }
        return content;
    }

    @Override
    protected CloudImage spyCloudImage(CloudImage image) {
        OpenstackCloudImage spy = Mockito.spy((OpenstackCloudImage) image);
        Mockito.when(spy.getNextInstanceId()).thenReturn("42");
        return spy;
    }

    private void initVMStart() throws Exception {
        stubFor(post("/v3/auth/tokens").willReturn(aResponse().withStatus(201).withHeader("X-Subject-Token", "test-subject-token")
                .withBody(getJSonTestAsStringWithReplaces("v3-auth-tokens.json", "REPLACE-PORT", String.valueOf(wireMockServer.port())))));
        stubFor(get("/v2.0/networks").willReturn(aResponse().withBodyFile("v2.0.networks.json")));
        stubFor(get("/v2.0/floatingips").willReturn(aResponse().withBodyFile("v2.0.floatingips.json")));
        stubFor(get("/v2.1/nova-id/images/detail").willReturn(aResponse().withBodyFile("v2.1-nova-id-images-detail.json")));
        stubFor(get("/v2.1/nova-id/flavors/detail").willReturn(aResponse().withBodyFile("v2.1-nova-id-flavors-detail.json")));
        stubFor(post("/v2.1/nova-id/servers").willReturn(aResponse().withStatus(201).withBodyFile("v2.1-nova-id-servers.json")));
        stubFor(get("/v2.1/nova-id/extensions").willReturn(aResponse().withBody("{}")));
    }

    @Test
    public void testRestore() throws Exception {
        initVMStart();

        // First call with VM already exist ... in other call the status will not exist
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(Scenario.STARTED).willSetStateTo(SCENARIO_STATE_INIT)
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-restore.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(SCENARIO_STATE_INIT).willSetStateTo(SCENARIO_STATE_RUN)
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-build.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(SCENARIO_STATE_RUN).willSetStateTo("stopping")
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-run.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs("stopping").willSetStateTo("stopped")
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-stopping.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs("stopped")
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-stopped.json")));

        // POST, do not add the content (should be ~"{ \"os-stop\" : null \"}" but only /action is a stop in scenario)
        stubFor(post("/v2.1/nova-id/servers/server-id/action").willReturn(aResponse().withStatus(202)));

        stubFor(delete("/v2.1/nova-id/servers/server-id").willReturn(aResponse().withStatus(204)));

        testSubSimple(wireMockServer.baseUrl() + "/v3", "default:my-tenant:ldap:foo", "bar", "region1", getTestYaml("Mock"));
    }

    @Test
    public void testNoError() throws Exception {
        initVMStart();

        // First call is for VMs restoration => "empty" (not status for VM created)
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(Scenario.STARTED).willSetStateTo(SCENARIO_STATE_INIT)
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-empty.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(SCENARIO_STATE_INIT).willSetStateTo(SCENARIO_STATE_RUN)
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-build.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(SCENARIO_STATE_RUN).willSetStateTo("stopping1")
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-run.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs("stopping1").willSetStateTo("stopping2")
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-stopping.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs("stopping2").willSetStateTo("stopped")
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-stopping.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs("stopped")
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-stopped.json")));

        // POST, do not add the content (should be ~"{ \"os-stop\" : null \"}" but only /action is a stop in scenario)
        stubFor(post("/v2.1/nova-id/servers/server-id/action").willReturn(aResponse().withStatus(202)));

        stubFor(delete("/v2.1/nova-id/servers/server-id").willReturn(aResponse().withStatus(204)));

        testSubSimple(wireMockServer.baseUrl() + "/v3", "default:my-tenant:ldap:foo", "bar", "region1", getTestYaml("Mock"));
    }

    @Test
    public void testErrorStatusEmpty() throws Exception {
        initVMStart();

        // Empty status for created instance
        stubFor(get("/v2.1/nova-id/servers/detail").willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-empty.json")));

        // /action is a stop in testSubSimple scenario (should be here even if not called in real life)
        stubFor(post("/v2.1/nova-id/servers/server-id/action").willReturn(aResponse().withStatus(202)));

        // DELETE is required for "terminate" instance even if status empty
        stubFor(delete("/v2.1/nova-id/servers/server-id").willReturn(aResponse().withStatus(204)));

        String err = testSubSimple(wireMockServer.baseUrl() + "/v3", "default:my-tenant:ldap:foo", "bar", "region1", getTestYaml("Mock"), true,
                false);
        Assert.assertNotNull(err);
        Assert.assertTrue(err.contains("Status cannot be found for instance"), err);
    }

    @Test
    public void testErrorStop() throws Exception {
        initVMStart();
        // State 'RUN' indefinitely even if stop engaged
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(Scenario.STARTED).willSetStateTo(SCENARIO_STATE_RUN)
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-empty.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(SCENARIO_STATE_RUN).willSetStateTo(SCENARIO_STATE_RUN)
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-run.json")));

        // POST, do not add the content
        stubFor(post("/v2.1/nova-id/servers/server-id/action").willReturn(aResponse().withStatus(500)));

        // DELETE is required for "dispose" client call at end
        stubFor(delete("/v2.1/nova-id/servers/server-id").willReturn(aResponse().withStatus(204)));

        String err = testSubSimple(wireMockServer.baseUrl() + "/v3", "default:my-tenant:ldap:foo", "bar", "region1", getTestYaml("Mock"), false,
                true);
        Assert.assertNotNull(err);
        Assert.assertTrue(err.contains("os-stop"), err);
    }

    @Test
    public void testErrorTerminate() throws Exception {
        initVMStart();

        // First call is for VMs restoration => "empty" (not status for VM created)
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(Scenario.STARTED).willSetStateTo(SCENARIO_STATE_INIT)
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-empty.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(SCENARIO_STATE_INIT).willSetStateTo(SCENARIO_STATE_RUN)
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-build.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(SCENARIO_STATE_RUN).willSetStateTo("stopping1")
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-run.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs("stopping1").willSetStateTo("stopping2")
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-stopping.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs("stopping2").willSetStateTo("stopped")
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-stopping.json")));
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs("stopped").willSetStateTo("stopped")
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-stopped.json")));

        // POST, do not add the content (should be ~"{ \"os-stop\" : null \"}" but only /action is a stop in scenario)
        stubFor(post("/v2.1/nova-id/servers/server-id/action").willReturn(aResponse().withStatus(202)));

        stubFor(delete("/v2.1/nova-id/servers/server-id").willReturn(aResponse().withStatus(500)));

        String err = testSubSimple(wireMockServer.baseUrl() + "/v3", "default:my-tenant:ldap:foo", "bar", "region1", getTestYaml("Mock"), false,
                true);
        Assert.assertNotNull(err);
        Assert.assertTrue(err.contains("DELETE"), err);
        Assert.assertTrue(err.contains("Server Error"), err);
    }

    @Test
    public void testErrorClientNovaNPEOnUpdateStatus() throws Exception {
        initVMStart();

        // First call is for VMs restoration => "empty" (not status for VM created)
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(Scenario.STARTED).willSetStateTo(SCENARIO_STATE_INIT)
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-empty.json")));

        // OpenStack response with a content without 'id' => will throw NPE on update status
        stubFor(get("/v2.1/nova-id/servers/detail").inScenario(SCENARIO).whenScenarioStateIs(SCENARIO_STATE_INIT).willSetStateTo(SCENARIO_STATE_INIT)
                .willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-not-defined.json")));

        // Termination due to NPE + unit 'testSubSimple' termination
        stubFor(delete("/v2.1/nova-id/servers/server-id").willReturn(aResponse().withStatus(202)));

        // unit 'testDubSimple' termination
        stubFor(post("/v2.1/nova-id/servers/server-id/action").willReturn(aResponse().withStatus(294)));

        testSubSimple(wireMockServer.baseUrl() + "/v3", "default:my-tenant:ldap:foo", "bar", "region1", getTestYaml("Mock"), true, true);

        // Test termination call after NPE
        verify(deleteRequestedFor(urlMatching("/v2.1/nova-id/servers/server-id")));
    }

    @Test
    public void testErrorClientNovaNPEOnRestore() throws Exception {
        initVMStart();

        // Bad response (no id) on every call (restore, status update, ...)
        stubFor(get("/v2.1/nova-id/servers/detail").willReturn(aResponse().withBodyFile("v2.1-nova-id-servers-detail-not-defined.json")));

        // Termination due to NPE + unit 'testSubSimple' termination
        stubFor(delete("/v2.1/nova-id/servers/server-id").willReturn(aResponse().withStatus(202)));

        // unit 'testDubSimple' termination
        stubFor(post("/v2.1/nova-id/servers/server-id/action").willReturn(aResponse().withStatus(204)));

        testSubSimple(wireMockServer.baseUrl() + "/v3", "default:my-tenant:ldap:foo", "bar", "region1", getTestYaml("Mock"), true, true);

        // Test termination call after NPE on restore
        verify(deleteRequestedFor(urlMatching("/v2.1/nova-id/servers/server-id")));
    }

}
