package jetbrains.buildServer.clouds.openstack;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;


public class OpenstackAgentProperties extends AgentLifeCycleAdapter {
    @NotNull private static final Logger LOG = Loggers.AGENT;
    @NotNull private final String metadataUrl = "http://169.254.169.254/openstack/latest/meta_data.json";
    @NotNull private final String userDataUrl = "http://169.254.169.254/openstack/latest/user_data";
    @NotNull private final BuildAgentConfigurationEx agentConfiguration;

    public OpenstackAgentProperties(@NotNull final BuildAgentConfigurationEx agentConfiguration, @NotNull EventDispatcher<AgentLifeCycleAdapter> dispatcher) {
        this.agentConfiguration = agentConfiguration;
        dispatcher.addListener(this);
    }

    @Override
    public void afterAgentConfigurationLoaded(@NotNull BuildAgent agent) {
        try {
            String metadata = readDataFromUrl(metadataUrl);
            LOG.info(String.format("Detected Openstack instance. Will write parameters from metadata: %s", metadataUrl));

            // fill metadata parameters
            String uuid = getParameterFromJson(metadata, "uuid");
            if (uuid != null) {
                LOG.debug(String.format("Setting %s to %s", OpenstackCloudParameters.OPENSTACK_INSTANCE_ID, uuid));
                agentConfiguration.addConfigurationParameter(OpenstackCloudParameters.OPENSTACK_INSTANCE_ID, uuid);
            }

            String name = getParameterFromJson(metadata, "name");
            if (name != null) {
                LOG.debug(String.format("Setting name to %s", name));
                agentConfiguration.setName(name);
            }

            LOG.debug(String.format("Setting %s to %s", OpenstackCloudParameters.AGENT_CLOUD_TYPE, OpenstackCloudParameters.CLOUD_TYPE));
            agentConfiguration.addConfigurationParameter(OpenstackCloudParameters.AGENT_CLOUD_TYPE, OpenstackCloudParameters.CLOUD_TYPE);

            // fill userdata parameters
            String userData = readDataFromUrl(userDataUrl);
            if (!StringUtil.isEmpty(userData)) {
                String decodedUserData = new String(Base64.decodeBase64(userData.getBytes()), "UTF-8");
                LOG.debug("UserData: " + decodedUserData);
                final CloudInstanceUserData cloudUserData = CloudInstanceUserData.deserialize(decodedUserData);
                if (cloudUserData != null) {

                    String serverAddress = cloudUserData.getServerAddress();
                    agentConfiguration.setServerUrl(serverAddress);

                    final Map<String, String> customParameters = cloudUserData.getCustomAgentConfigurationParameters();
                    for (Map.Entry<String, String> entry : customParameters.entrySet()) {
                        agentConfiguration.addConfigurationParameter(entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("It seems build-agent launched at non-Openstack instance.");
            LOG.error(e.getMessage());
        }
    }

    private static String readDataFromUrl(String sURL) throws IOException {
        String data = "";
        InputStream in = new URL(sURL).openStream();
        try {
            data = IOUtils.toString(in);
        } finally {
            IOUtils.closeQuietly(in);
        }
        return data.trim();
    }

    private static String getParameterFromJson(String rawJson, String parameterName) {
        JsonElement jp = new JsonParser().parse(rawJson);
        String parameter = jp.getAsJsonObject().get(parameterName).toString();
        if (!StringUtil.isEmpty(parameter)) {
            parameter = parameter.replaceAll("^\"|\"$", "");  // trim leading and ending double quotes
        }
        return parameter;
    }

}
