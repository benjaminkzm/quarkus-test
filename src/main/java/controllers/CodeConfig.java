package controllers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import com.horstmann.codecheck.ResourceLoader;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CodeConfig implements ResourceLoader {

    @Override
    public InputStream loadResource(String path) throws IOException {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("resources/" + path);
        if (inputStream == null) {
            throw new IOException("Resource not found: " + path);
        }
        return inputStream;
    }

    @Override
    public String getProperty(String key) {
        return getString(key);
    }

    public <T> Optional<T> getOptionalValue(String key, Class<T> type) {
        Config config = ConfigProvider.getConfig();
        return config.getOptionalValue(key, type);
    }

    public String getString(String key) {
        return getOptionalValue(key, String.class).orElse(null);
    }

    public boolean hasPath(String key) {
        return getOptionalValue(key, String.class).isPresent();
    }
}
