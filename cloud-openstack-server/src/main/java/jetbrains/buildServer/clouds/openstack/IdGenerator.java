package jetbrains.buildServer.clouds.openstack;

import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.NotNull;

public class IdGenerator {

    private final AtomicLong lastId = new AtomicLong(System.currentTimeMillis());

    @NotNull
    public String next() {
        long now = System.currentTimeMillis();
        while (true) {
            long lastTime = lastId.get();
            if (lastTime >= now) {
                now = lastTime + 1;
            }
            if (lastId.compareAndSet(lastTime, now)) {
                return String.valueOf(now);
            }
        }
    }

}
