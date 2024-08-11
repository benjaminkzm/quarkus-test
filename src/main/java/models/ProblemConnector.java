package models;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.horstmann.codecheck.Util;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProblemConnector {
    private final ProblemConnection delegate;

    @Inject
    public ProblemConnector(@ConfigProperty(name = "com.horstmann.codecheck.s3.region") String s3Region) {
        Config config = ConfigProvider.getConfig();
        // Validate configuration and initialize delegate
        validateConfig(config);
        delegate = new ProblemLocalConnection(config);
    }

    public void write(byte[] contents, String repo, String key) throws IOException {
        delegate.write(contents, repo, key);
    }

    public void delete(String repo, String key) throws IOException {
        delegate.delete(repo, key);
    }

    public byte[] read(String repo, String key) throws IOException {
        return delegate.read(repo, key);
    }

    private void validateConfig(Config config) {
        String localPath = config.getValue("com.horstmann.codecheck.s3.local", String.class);
        if (localPath == null || localPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Configuration 'com.horstmann.codecheck.s3.local' must be set and non-empty.");
        }
    }
}

interface ProblemConnection {
    void write(byte[] contents, String repo, String key) throws IOException;
    void delete(String repo, String key) throws IOException;
    byte[] read(String repo, String key) throws IOException;
}

@Singleton
class ProblemLocalConnection implements ProblemConnection {
    private Path root;
    private static final Logger logger = Logger.getLogger(ProblemLocalConnection.class);

    public ProblemLocalConnection(Config config) {
        String localPath = config.getValue("com.horstmann.codecheck.s3.local", String.class);
        if (localPath == null || localPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Configuration 'com.horstmann.codecheck.s3.local' must be set and non-empty.");
        }
        this.root = Path.of(localPath);
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            logger.error("Cannot create directory " + root.toAbsolutePath(), ex);
            throw new RuntimeException("Cannot create root directory", ex);
        }
    }

    @Override
    public void write(byte[] contents, String repo, String key) throws IOException {
        Path repoPath = root.resolve(repo);
        Files.createDirectories(repoPath);
        Path newFilePath = repoPath.resolve(key + ".zip");
        try {
            Files.write(newFilePath, contents);
        } catch (IOException ex) {
            String bytes = Arrays.toString(contents);
            logger.error("Cannot write to file " + newFilePath.toAbsolutePath() + " in repository " + repo, ex);
            throw ex;
        }
    }

    @Override
    public void delete(String repo, String key) throws IOException {
        Path repoPath = root.resolve(repo);
        Path directoryPath = repoPath.resolve(key);
        try {
            Util.deleteDirectory(directoryPath);
        } catch (IOException ex) {
            logger.error("Cannot delete directory " + directoryPath.toAbsolutePath() + " in repository " + repo, ex);
            throw ex;
        }
    }

    @Override
    public byte[] read(String repo, String key) throws IOException {
        Path repoPath = root.resolve(repo);
        Path filePath = repoPath.resolve(key + ".zip");
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException ex) {
            logger.error("Cannot read file " + filePath.toAbsolutePath() + " from repository " + repo, ex);
            throw ex;
        }
    }
}
