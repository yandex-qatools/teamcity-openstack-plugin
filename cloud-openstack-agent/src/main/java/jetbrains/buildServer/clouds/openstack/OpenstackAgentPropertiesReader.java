package jetbrains.buildServer.clouds.openstack;

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
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class OpenstackAgentPropertiesReader extends AgentLifeCycleAdapter {
    @NotNull private static final Logger LOG = Loggers.AGENT;
    @NotNull private final String metadataUrl = "http://169.254.169.254/openstack/latest/meta_data.json";
    @NotNull private final BuildAgentConfigurationEx agentConfiguration;

    public OpenstackAgentPropertiesReader(@NotNull final BuildAgentConfigurationEx agentConfiguration, @NotNull EventDispatcher<AgentLifeCycleAdapter> dispatcher) {
        this.agentConfiguration = agentConfiguration;
        dispatcher.addListener(this);
    }

    @Override
    public void afterAgentConfigurationLoaded(@NotNull BuildAgent agent) {
        try {
            String rawMetadata = readDataFromUrl(metadataUrl);
            LOG.info(String.format("Detected Openstack instance. Will write parameters from metadata: %s", metadataUrl));

            JsonElement metadataElement = new JsonParser().parse(rawMetadata);

            JsonElement teamCityUserData = metadataElement.getAsJsonObject().get("meta");
            Type type = new TypeToken<HashMap<String, String>>() {}.getType();
            HashMap<String,String> customParameters = new Gson().fromJson(teamCityUserData, type);

            if (!customParameters.containsKey(OpenstackCloudParameters.SERVER_URL)) {
                LOG.info("Not my OpenStack instance, skipping");
                return;
            }

            for (Map.Entry<String, String> entry : customParameters.entrySet()) {
                if (Objects.equals(entry.getKey(), OpenstackCloudParameters.SERVER_URL)) {
                    agentConfiguration.setServerUrl(entry.getValue());
                } else {
                    agentConfiguration.addConfigurationParameter(entry.getKey(), entry.getValue());
                }
            }

            String uuid = metadataElement.getAsJsonObject().get("uuid").getAsString();
            if (uuid != null) {
                LOG.info(String.format("Setting %s to %s", OpenstackCloudParameters.OPENSTACK_INSTANCE_ID, uuid));
                agentConfiguration.addConfigurationParameter(OpenstackCloudParameters.OPENSTACK_INSTANCE_ID, uuid);
            }

            String name = metadataElement.getAsJsonObject().get("name").getAsString();
            if (name != null) {
                agentConfiguration.setName(name);
            }

            LOG.info(String.format("Setting %s to %s", OpenstackCloudParameters.AGENT_CLOUD_TYPE, OpenstackCloudParameters.CLOUD_TYPE));
            agentConfiguration.addConfigurationParameter(OpenstackCloudParameters.AGENT_CLOUD_TYPE, OpenstackCloudParameters.CLOUD_TYPE);

        } catch (Exception e) {
            LOG.info("It seems build-agent launched at non-Openstack instance.");
            LOG.error(e.getMessage());
        }
    }

    private static String readDataFromUrl(String sURL) throws IOException {
        String data = "";
        InputStream in = new URL(sURL).openStream();
        try {
            data = IOUtils.toString(in, StandardCharsets.UTF_8.name());
        } finally {
            IOUtils.closeQuietly(in);
        }
        return data.trim();
    }

}
