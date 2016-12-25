package jetbrains.buildServer.clouds.openstack;

import jetbrains.buildServer.clouds.CloudClientFactory;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudRegistrar;
import jetbrains.buildServer.clouds.CloudState;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.NamedDeamonThreadFactory;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class OpenstackCloudClientFactory implements CloudClientFactory {
    @NotNull private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT);
    @NotNull private final String cloudProfileSettings;
    @NotNull private final ServerPaths serverPaths;

    public OpenstackCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                       @NotNull final PluginDescriptor pluginDescriptor,
                                       @NotNull final ServerPaths serverPaths) {
        cloudProfileSettings = pluginDescriptor.getPluginResourcesPath("profile-settings.jsp");
        this.serverPaths = serverPaths;
        cloudRegistrar.registerCloudFactory(this);
    }

    @NotNull
    public String getCloudCode() {
        return OpenstackCloudParameters.CLOUD_TYPE;
    }

    @NotNull
    public String getDisplayName() {
        return OpenstackCloudParameters.CLOUD_DISPLAY_NAME;
    }

    @Nullable
    public String getEditProfileUrl() {
        return cloudProfileSettings;
    }

    @NotNull
    public Map<String, String> getInitialParameterValues() {
        return Collections.emptyMap();
    }

    @NotNull
    public PropertiesProcessor getPropertiesProcessor() {
        return new PropertiesProcessor() {
            @NotNull
            public Collection<InvalidProperty> process(@NotNull final Map<String, String> properties) {
                return Collections.emptyList();
            }
        };
    }

    public boolean canBeAgentOfType(@NotNull final AgentDescription agentDescription) {
        final Map<String, String> configParams = agentDescription.getConfigurationParameters();
        return configParams.containsValue(OpenstackCloudParameters.CLOUD_TYPE);
    }

    @NotNull
    public OpenstackCloudClient createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params) {
        return new OpenstackCloudClient(params, serverPaths, new ExecutorServiceFactory() {
            @NotNull
            public ScheduledExecutorService createExecutorService(@NotNull final String duty) {
                return Executors.newSingleThreadScheduledExecutor(new NamedDeamonThreadFactory("openstack-" + duty));
            }
        });
    }
}
