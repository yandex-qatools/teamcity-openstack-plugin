package jetbrains.buildServer.clouds.openstack.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.WriterAppender;
import org.apache.log4j.spi.LoggingEvent;

public class Lo4jBeanAppender extends WriterAppender {

    private static List<String> logs = new ArrayList<>();

    @Override
    public void append(LoggingEvent event) {
        logs.add("[" + event.getLevel().toString() + "] " + event.getMessage().toString());
    }

    public static void clear() {
        logs.clear();
    }

    public static boolean contains(String string) {
        for (String s : logs) {
            if (s.contains(string)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEmpty() {
        return logs.isEmpty();
    }

}
