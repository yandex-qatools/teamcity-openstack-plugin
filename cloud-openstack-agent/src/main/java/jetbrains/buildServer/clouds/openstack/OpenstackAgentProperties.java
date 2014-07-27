package jetbrains.buildServer.clouds.openstack;

import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.UnknownHostException;


public class OpenstackAgentProperties extends AgentLifeCycleAdapter {

    public OpenstackAgentProperties(@NotNull EventDispatcher<AgentLifeCycleAdapter> dispatcher) {
        dispatcher.addListener(this);
    }

    @Override
    public void beforeAgentConfigurationLoaded(@NotNull BuildAgent agent) {
        BuildAgentConfiguration configuration = agent.getConfiguration();
        try {
            configuration.addSystemProperty("agent.address", InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
