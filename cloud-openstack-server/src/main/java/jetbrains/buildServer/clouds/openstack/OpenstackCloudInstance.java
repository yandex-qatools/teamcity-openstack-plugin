package jetbrains.buildServer.clouds.openstack;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.PropertiesUtil;
import jetbrains.buildServer.util.WaitFor;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static jetbrains.buildServer.clouds.openstack.OpenstackCloudConstants.IMAGE_ID_PARAM_NAME;
import static jetbrains.buildServer.clouds.openstack.OpenstackCloudConstants.INSTANCE_ID_PARAM_NAME;


public abstract class OpenstackCloudInstance implements CloudInstance {
  @NotNull
  private static final Logger LOG = Logger.getLogger(OpenstackCloudInstance.class);
  private static final int STATUS_WAITING_TIMEOUT = 30 * 1000;

  @NotNull
  private final String myId;
  @NotNull
  private final OpenstackCloudImage myImage;
  @NotNull
  private final Date myStartDate;
  @NotNull
  private final File myBaseDir;
  @NotNull
  private final AtomicBoolean myIsAgentExtracted = new AtomicBoolean(false);
  @NotNull
  private final AtomicBoolean myIsAgentPermissionsUpdated = new AtomicBoolean(false);
  @NotNull
  private final AtomicBoolean myIsConfigPatched = new AtomicBoolean(false);

  @NotNull
  private volatile InstanceStatus myStatus;
  @Nullable
  private volatile CloudErrorInfo myErrorInfo;

  @NotNull
  private final ScheduledExecutorService myAsync;

  public OpenstackCloudInstance(@NotNull final OpenstackCloudImage image, @NotNull final String instanceId, @NotNull ScheduledExecutorService executor) {
    myImage = image;
    myBaseDir = createBaseDir(); // can set status to ERROR, so must be after "myStatus = ..." line
    myBaseDir.deleteOnExit();
    myStatus = InstanceStatus.SCHEDULED_TO_START;
    myId = instanceId;
    myStartDate = new Date();
    myAsync = executor;
  }

  public abstract boolean isRestartable();

  @NotNull
  protected File getBaseDir() {
    return myBaseDir;
  }

  @NotNull
  private File createBaseDir() {
    try {
      return FileUtil.createTempDirectory("tc_buildAgent_", "");
    } catch (final IOException e) {
      processError(e);
      return new File("");
    }
  }

  @NotNull
  public String getInstanceId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return OpenstackCloudClient.generateAgentName(myImage, myId) + " (" + myBaseDir.getAbsolutePath() + ")";
  }

  @NotNull
  public String getImageId() {
    return myImage.getId();
  }

  @NotNull
  public OpenstackCloudImage getImage() {
    return myImage;
  }

  @NotNull
  public Date getStartedTime() {
    return myStartDate;
  }

  public String getNetworkIdentity() {
    return "clouds.openstack." + getImageId() + "." + myId;
  }

  @NotNull
  public InstanceStatus getStatus() {
    return myStatus;
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorInfo;
  }

  public boolean containsAgent(@NotNull final AgentDescription agentDescription) {
    final Map<String, String> configParams = agentDescription.getConfigurationParameters();
    return myId.equals(configParams.get(INSTANCE_ID_PARAM_NAME)) &&
            getImageId().equals(configParams.get(IMAGE_ID_PARAM_NAME));
  }

  public void start(@NotNull final CloudInstanceUserData data) {
    myStatus = InstanceStatus.STARTING;

    myAsync.submit(ExceptionUtil.catchAll("start local cloud: " + this, new StartAgentCommand(data)));
  }

  public void restart() {
    waitForStatus(InstanceStatus.RUNNING);
    myStatus = InstanceStatus.RESTARTING;
    try {
      doStop();
      Thread.sleep(3000);
      doStart();
    } catch (final Exception e) {
      processError(e);
    }
  }

  public void terminate() {
    myStatus = InstanceStatus.STOPPING;
    try {
      doStop();
      myStatus = InstanceStatus.STOPPED;
      cleanupStoppedInstance();
    } catch (final Exception e) {
      processError(e);
    }
  }

  protected abstract void cleanupStoppedInstance();

  private void waitForStatus(@NotNull final InstanceStatus status) {
    new WaitFor(STATUS_WAITING_TIMEOUT) {
      @Override
      protected boolean condition() {
        return myStatus == status;
      }
    };
  }

  private void processError(@NotNull final Exception e) {
    final String message = e.getMessage();
    LOG.error(message, e);
    myErrorInfo = new CloudErrorInfo(message, message, e);
    myStatus = InstanceStatus.ERROR;
  }

  private void doStart() throws Exception {
    exec("start");
  }

  private void doStop() throws Exception {
    exec("stop", "force");
  }

  private void exec(@NotNull final String... params) throws Exception {
    final GeneralCommandLine cmd = new GeneralCommandLine();
    final File workDir = new File(myBaseDir, "bin");
    cmd.setWorkDirectory(workDir.getAbsolutePath());
    final Map<String, String> env = new HashMap<String, String>(System.getenv());
    //fix Java
    env.put("JAVA_HOME", System.getProperty("java.home"));

    if (SystemInfo.isWindows) {
      cmd.setExePath("cmd.exe");
      cmd.addParameter("/c");
      cmd.addParameter(new File(workDir, "agent.bat").getAbsolutePath());
    } else {
      cmd.setExePath("/bin/sh");
      cmd.addParameter(new File(workDir, "agent.sh").getAbsolutePath());
    }
    cmd.addParameters(params);

    LOG.info("Starting agent: " + cmd.getCommandLineString());
    ExecResult execResult = SimpleCommandLineProcessRunner.runCommand(cmd, null);
    LOG.info("Execution finished: " + execResult.getExitCode());
    LOG.info(execResult.getStdout());
    LOG.info(execResult.getStderr());
  }

  private class StartAgentCommand implements Runnable {
    private final CloudInstanceUserData myData;

    public StartAgentCommand(@NotNull final CloudInstanceUserData data) {
      myData = data;
    }

    private void copyAgentToDestFolder() throws IOException {
      //do not re-extract agent
      if (myIsAgentExtracted.getAndSet(true)) return;

      final File agentHomeDir = myImage.getAgentHomeDir();
      FileUtil.copyDir(agentHomeDir, myBaseDir, new FileFilter() {
          private final Set<String> ourDirsToNotToCopy = new HashSet<String>() {{
              Collections.addAll(this, "work", "temp", "system", "contrib");
          }};

          public boolean accept(@NotNull final File file) {
              return !file.isDirectory() || !file.getParentFile().equals(agentHomeDir) || !ourDirsToNotToCopy.contains(file.getName());
          }
      });
    }

    private void updateAgentProperties(@NotNull final CloudInstanceUserData data) throws IOException {
      File inConfigFile = new File(new File(myBaseDir, "conf"), "buildAgent.properties"), outConfigFile = inConfigFile;
      if (!inConfigFile.isFile()) {
        inConfigFile = new File(new File(myBaseDir, "conf"), "buildAgent.dist.properties");
        if (!inConfigFile.isFile()) {
          inConfigFile = null;
        }
      }
      final Properties config = PropertiesUtil.loadProperties(inConfigFile);

      config.put("serverUrl", data.getServerAddress());
      config.put("workDir", "../work");
      config.put("tempDir", "../temp");
      config.put("systemDir", "../system");

      //agent name and auth-token must be patched only once
      if (!myIsConfigPatched.getAndSet(true)) {
        config.put("name", data.getAgentName());
        config.put("authorizationToken", data.getAuthToken());
      }
      for (final Map.Entry<String, String> param : data.getCustomAgentConfigurationParameters().entrySet()) {
        config.put(param.getKey(), param.getValue());
      }
      config.put(IMAGE_ID_PARAM_NAME, getImageId());
      config.put(INSTANCE_ID_PARAM_NAME, myId);
      PropertiesUtil.storeProperties(config, outConfigFile, null);
    }

    private void updateAgentPermissions() {
      if (SystemInfo.isWindows) return;
      if (!myIsAgentPermissionsUpdated.compareAndSet(false, true)) return;

      for (String dir : new String[]{"bin", "launcher/bin"}) {
        final File basePath = new File(myImage.getAgentHomeDir(), dir);
        final File[] files = basePath.listFiles(new FilenameFilter() {
          //@Override
          public boolean accept(File dir, String name) {
            return name.endsWith(".sh");
          }
        });

        if (files == null) {
          LOG.warn("Failed to list files under " + basePath);
          continue;
        }

        for (File file : files) {
          try {
            FileUtil.setExectuableAttribute(file.getAbsolutePath(), true);
          } catch (IOException e) {
            LOG.warn("Failed to set writable permission for " + file + ". " + e.getMessage());
          }
        }
      }
    }

    //@Override
    public void run() {
      try {

        if (myImage.isEternalStarting()) return;

        copyAgentToDestFolder();
        updateAgentPermissions();
        updateAgentProperties(myData);

        doStart();
        myStatus = InstanceStatus.RUNNING;
      } catch (final Exception e) {
        processError(e);
      }
    }
  }
}
