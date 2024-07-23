package models;

import org.eclipse.microprofile.config.ConfigProvider;

public class Config {

    public static String getString(String key) {
        return ConfigProvider.getConfig().getValue(key, String.class);
    }

    public static boolean hasPath(String key) {
        return ConfigProvider.getConfig().getOptionalValue(key, String.class).isPresent();
    }
}