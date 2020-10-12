package jetbrains.buildServer.clouds.openstack.util;

import java.util.Collection;
import java.util.Map;

import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.util.StringUtil;

public class TestCloudClientParameters extends CloudClientParameters {

    private Map<String, String> params;

    public TestCloudClientParameters(Map<String, String> params) {
        this.params = params;

        // Remove empty parameters
        params.entrySet().removeIf(e -> StringUtil.isEmpty(e.getValue()));
    }

    @Override
    public String getProfileId() {
        throw new UnsupportedOperationException("NYI");
    }

    @Override
    public String getParameter(String name) {
        return params.get(name);
    }

    @Override
    public Collection<String> listParameterNames() {
        throw new UnsupportedOperationException("NYI");
    }

    @Override
    public Collection<CloudImageParameters> getCloudImages() {
        throw new UnsupportedOperationException("NYI");
    }

    @Override
    public Map<String, String> getParameters() {
        throw new UnsupportedOperationException("NYI");
    }

    @Override
    public String getProfileDescription() {
        throw new UnsupportedOperationException("NYI");
    }

}
