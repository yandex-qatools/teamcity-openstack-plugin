package jetbrains.buildServer.clouds.openstack.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.WriterAppender;
import org.apache.log4j.spi.LoggingEvent;

public class Lo4jBeanAppender extends WriterAppender {

    /** Logger. Multithread usage => synchronized usage (simply solution for UT) */
    private static List<String> logs = new ArrayList<>();

    @Override
    public void append(LoggingEvent event) {
        synchronized (logs) {
            logs.add("[" + event.getLevel().toString() + "] " + event.getMessage().toString());
        }
    }

    public static void clear() {
        synchronized (logs) {
            logs.clear();
        }
    }

    public static boolean contains(String string) {
        synchronized (logs) {
            for (String s : logs) {
                if (s.contains(string)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean isEmpty() {
        return logs.isEmpty();
    }

}
