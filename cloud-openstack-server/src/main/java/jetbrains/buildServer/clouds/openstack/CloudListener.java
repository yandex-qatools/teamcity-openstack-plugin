package jetbrains.buildServer.clouds.openstack;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.instances.BeforeStartInstanceArguments;
import jetbrains.buildServer.clouds.server.instances.CloudEventListener;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Date;

/**
 * Created by vshakhov on 24/05/16.
 */
public class CloudListener implements CloudEventListener {

    private static final Logger logger = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT);

    private String orNull(String a) {
        if (a == null) {
            return "null";
        }
        return a.toString().replace(" ", "_");
    }

    private String orNullOrDefault(String a, String def) {
        if (a == null)
            return def;
        return a.toString().replace(" ", "_");
    }

    private String orNull(Date a) {
        if (a == null) {
            return "null";
        }
        return a.toString().replace(" ", "_");
    }

    private String orNull(Throwable a) {
        if (a == null) {
            return "null";
        }
        return orNullOrDefault(a.getMessage(), a.getClass().getName());
    }

    private String agent(SBuildAgent agent) {
        if (agent == null)
            return "null";
        return orNull(agent.getName())
                + " " + orNull(agent.getHostAddress())
                + " " + agent.getPort()
                + " " + orNull(agent.getHostName())
                + " " + orNull(agent.getAuthorizationToken())
                + " " + orNull(agent.getRegistrationTimestamp())
                + " " + orNull(agent.getVersion())
                + " " + orNull(agent.getUnregistrationComment())
                + " " + orNull(agent.getAuthorizeComment().getComment())
                + " " + orNull(agent.getAgentStatusRestoringTimestamp())
                + " " + orNull(agent.getLastCommunicationTimestamp())
                + " " + orNull(agent.getStatusComment().getComment())
                ;
    }

    @Override
    public void cloudClientFactoryRegistered(@NotNull String s) {

    }

    @Override
    public void instanceStatusChanged(@NotNull CloudProfile cloudProfile, @NotNull CloudInstance cloudInstance) {
        logger.debug("CL: status_changed " + cloudProfile.getProfileId() + " " + cloudInstance.getName());
    }

    @Override
    public void instanceGone(@NotNull String s, @NotNull String s1, @NotNull String s2) {
        logger.debug("CL: instance_gone " + s + " " + s1 + " "  + s2);
    }

    @Override
    public void beforeInstanceStarted(@NotNull CloudProfile cloudProfile, @NotNull CloudImage cloudImage, @NotNull BeforeStartInstanceArguments beforeStartInstanceArguments) {
    }

    @Override
    public void instanceStarting(@NotNull CloudProfile cloudProfile, @NotNull CloudInstance cloudInstance) {

    }

    @Override
    public void instanceTerminating(@NotNull CloudProfile cloudProfile, @NotNull CloudInstance cloudInstance) {

    }

    @Override
    public void instanceAgentMatched(@NotNull CloudProfile cloudProfile, @NotNull CloudInstance cloudInstance, @NotNull SBuildAgent sBuildAgent) {
        logger.debug("CL: matched " + agent(sBuildAgent) + " " + cloudProfile.getProfileId() + " " + cloudInstance.getName());
    }

    @Override
    public void instanceAgentUnmatched(@NotNull String s, @NotNull String s1, @NotNull String s2, @NotNull SBuildAgent sBuildAgent) {
        logger.debug("CL: unmatched " + agent(sBuildAgent) + " " + s + " " + s1 + " " + s2);
    }

    @Override
    public void profilesUpdated(@NotNull Collection<CloudProfile> collection, boolean b) {

    }

    @Override
    public void profileUpdated(@NotNull CloudProfile cloudProfile) {

    }

    @Override
    public void profileRemoved(@NotNull String s) {

    }

    @Override
    public void instanceFailedToStop(@NotNull CloudProfile cloudProfile, @NotNull CloudInstance cloudInstance, @Nullable Throwable throwable) {
        logger.debug("CL: instance_failed_to_stop " + cloudProfile.getCloudCode() + " " + cloudInstance.getName() + " " + orNull(throwable));
    }
}
