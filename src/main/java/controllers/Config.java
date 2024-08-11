package controllers;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.microprofile.config.ConfigProvider;

import com.horstmann.codecheck.ResourceLoader;

import jakarta.inject.Singleton;

@Singleton
public class Config implements ResourceLoader {

    private final org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();

    @Override
    public InputStream loadResource(String path) throws IOException {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (inputStream == null) {
            throw new IOException("Resource not found: " + path);
        }
        return inputStream;
    }

    @Override
    public String getProperty(String key) {
        return config.getOptionalValue(key, String.class).orElse(null);
    }
}
