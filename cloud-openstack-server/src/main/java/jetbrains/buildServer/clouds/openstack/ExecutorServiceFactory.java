package jetbrains.buildServer.clouds.openstack;

import org.jetbrains.annotations.NotNull;
import java.util.concurrent.ScheduledExecutorService;


public interface ExecutorServiceFactory {
  @NotNull
  ScheduledExecutorService createExecutorService(@NotNull String duty);
}
