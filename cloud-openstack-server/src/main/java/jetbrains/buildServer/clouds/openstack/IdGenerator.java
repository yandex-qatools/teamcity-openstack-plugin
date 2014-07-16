package jetbrains.buildServer.clouds.openstack;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

public class IdGenerator {
    private final AtomicInteger myNextId = new AtomicInteger();

    @NotNull
    public String next() {
        System.out.println("IdGenerator.next");
        return String.valueOf(myNextId.incrementAndGet());
    }
}
