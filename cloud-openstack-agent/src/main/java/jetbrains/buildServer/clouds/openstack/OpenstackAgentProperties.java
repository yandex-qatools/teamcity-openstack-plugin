package jetbrains.buildServer.clouds.openstack;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;

import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.EventDispatcher;

public class OpenstackAgentProperties extends AgentLifeCycleAdapter {
    @NotNull
    private static final Logger LOG = Loggers.AGENT;
    @NotNull
    private final BuildAgentConfigurationEx agentConfiguration;
    @NotNull
    private String metadataUrl = "http://169.254.169.254/openstack/latest/meta_data.json"; // NOSONAR: Openstack IP doesn't change

    private static final String LOG_SETTINGS = "Setting %s to %s";

    public OpenstackAgentProperties(@NotNull final BuildAgentConfigurationEx agentConfiguration,
            @NotNull EventDispatcher<AgentLifeCycleAdapter> dispatcher) {
        this.agentConfiguration = agentConfiguration;
        dispatcher.addListener(this);
    }

    @Override
    public void afterAgentConfigurationLoaded(@NotNull BuildAgent agent) {
        try {
            if ("true".equals(agentConfiguration.getConfigurationParameters().get(OpenstackCloudParameters.AGENT_METADATA_DISABLE))) {
                LOG.info("Openstack metadata usage disabled (agent configuration not overridden)");
                return;
            }

            String rawMetadata = readDataFromUrl(metadataUrl);
            LOG.info(String.format("Detected Openstack instance. Will write parameters from metadata: %s", metadataUrl));

            JsonElement metadataElement = new JsonParser().parse(rawMetadata);

            String uuid = metadataElement.getAsJsonObject().get("uuid").getAsString();
            if (uuid != null) {
                LOG.info(String.format(LOG_SETTINGS, OpenstackCloudParameters.OPENSTACK_INSTANCE_ID, uuid));
                agentConfiguration.addConfigurationParameter(OpenstackCloudParameters.OPENSTACK_INSTANCE_ID, uuid);
            }

            String name = metadataElement.getAsJsonObject().get("name").getAsString();
            if (name != null) {
                LOG.info(String.format(LOG_SETTINGS, "name", name));
                agentConfiguration.setName(name);
            }

            // user data is optionnal
            JsonElement teamCityUserData = metadataElement.getAsJsonObject().get("meta");
            if (teamCityUserData != null) {
                Type type = new TypeToken<HashMap<String, String>>() {
                }.getType();
                HashMap<String, String> customParameters = new Gson().fromJson(teamCityUserData, type);
                for (Map.Entry<String, String> entry : customParameters.entrySet()) {
                    LOG.info(String.format(LOG_SETTINGS, entry.getKey(), entry.getValue()));
                    agentConfiguration.addConfigurationParameter(entry.getKey(), entry.getValue());
                    if (OpenstackCloudParameters.AGENT_CLOUD_IP.equals(entry.getKey())) {
                        agentConfiguration.addAlternativeAgentAddress(entry.getValue());
                    }
                }
            }

            LOG.info(String.format(LOG_SETTINGS, OpenstackCloudParameters.AGENT_CLOUD_TYPE, OpenstackCloudParameters.CLOUD_TYPE));
            agentConfiguration.addConfigurationParameter(OpenstackCloudParameters.AGENT_CLOUD_TYPE, OpenstackCloudParameters.CLOUD_TYPE);

        } catch (IOException e) {
            LOG.info(String.format("It seems build-agent launched at non-Openstack instance: %s", e.getMessage()));
        } catch (Exception e) {
            LOG.error(String.format("Unknow problem on Openstack plugin: %s.", e.getMessage()), e);
        }
    }

    private static String readDataFromUrl(String sURL) throws IOException {
        String data = "";
        URL url = new URL(sURL);
        HttpURLConnection ctx = (HttpURLConnection) url.openConnection();
        if (ctx.getResponseCode() != HttpServletResponse.SC_OK) {
            throw new IOException(String.format("Http code %s on meta data URL", ctx.getResponseCode()));
        }
        try (InputStream in = url.openStream()) {
            data = IOUtils.toString(in, StandardCharsets.UTF_8.name());
        }
        return data.trim();
    }

    /**
     * Override Meta Data URL (using only for unit test)
     * 
     * @param url New URL
     */
    protected void setUrlMetaData(String url) {
        this.metadataUrl = url;
    }

    /**
     * Get Meta Data URL (using only for unit test)
     */
    protected String getMetadataUrl() {
        return this.metadataUrl;
    }
}
