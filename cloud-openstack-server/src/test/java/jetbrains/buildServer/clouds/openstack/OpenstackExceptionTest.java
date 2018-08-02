package jetbrains.buildServer.clouds.openstack;

import org.testng.annotations.Test;

public class OpenstackExceptionTest {

    @Test
    public void testExceptions() {
        new OpenstackException();
        new OpenstackException("Test");
        new OpenstackException("Test", new Exception());
        new OpenstackException(new Exception());
    }
}
