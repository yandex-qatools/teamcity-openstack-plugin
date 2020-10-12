package jetbrains.buildServer.serverSide;

import java.util.HashMap;
import java.util.Map;

import jetbrains.buildServer.serverSide.TeamCityProperties.Model;

public class TeamCityPropertiesMock {

    private static Model initialModel = null;

    private static class ModelMock implements Model {

        private Map<String, String> properties = new HashMap<>();

        @Override
        public String getPropertyOrNull(String key) {
            return properties.get(key);
        }

        public void storeValue(String key, String defaultValue) {
            properties.put(key, defaultValue);
        }

        @Override
        public void storeDefaultValue(String key, String defaultValue) {
            // not used
        }

        @Override
        public Map<String, String> getSystemProperties() {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        public Map<String, String> getUserDefinedProperties() {
            throw new UnsupportedOperationException("Not used");
        }

    };

    public static void addProperty(String key, String value) {
        if (initialModel == null) {
            initialModel = TeamCityProperties.getModel();
            TeamCityProperties.setModel(new ModelMock());
        }
        ((ModelMock) TeamCityProperties.getModel()).storeValue(key, value);
    }

    public static void reset() {
        if (initialModel == null) {
            throw new UnsupportedOperationException("Please use 'addProperty' first");
        }
        TeamCityProperties.setModel(initialModel);
        initialModel = null;
    }
}
