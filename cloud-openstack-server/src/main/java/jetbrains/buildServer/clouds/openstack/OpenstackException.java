package jetbrains.buildServer.clouds.openstack;

public class OpenstackException extends Exception {

    private static final long serialVersionUID = -8835657293823900872L;

    public OpenstackException() {
        super();
    }

    public OpenstackException(String message) {
        super(message);
    }

    public OpenstackException(Throwable exception) {
        super(exception);
    }

    public OpenstackException(String message, Throwable exception) {
        super(message, exception);
    }
}
