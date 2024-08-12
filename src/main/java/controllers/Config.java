package controllers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import com.horstmann.codecheck.ResourceLoader;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class Config implements ResourceLoader {

    private final Config config;

    @Inject
    public Config(Config config) {
        this.config = config;
    }

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
        return config.getOptionalValue(key, type);
    }

    public String getString(String key) {
        return getOptionalValue(key, String.class).orElse(null);
    }

    public boolean hasPath(String key) {
        return getOptionalValue(key, String.class).isPresent();
    }
}
