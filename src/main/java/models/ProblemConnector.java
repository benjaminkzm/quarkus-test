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
}

interface ProblemConnection {
    void write(byte[] contents, String repo, String key) throws IOException;
    void delete(String repo, String key) throws IOException;
    byte[] read(String repo, String key) throws IOException;
}
/*
class ProblemS3Connection implements ProblemConnection {
    private String bucketSuffix;
    private AmazonS3 amazonS3;
    private static final Logger logger = Logger.getLogger(ProblemS3Connection.class);

    public ProblemS3Connection(Config config) {
        String awsAccessKey = config.getValue("com.horstmann.codecheck.aws.accessKey", String.class);
        String awsSecretKey = config.getValue("com.horstmann.codecheck.aws.secretKey", String.class);
        String region = config.getValue("com.horstmann.codecheck.s3.region", String.class);
        amazonS3 = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsAccessKey, awsSecretKey)))
                .withRegion(region)
                .withForceGlobalBucketAccessEnabled(true)
                .build();

        bucketSuffix = config.getValue("com.horstmann.codecheck.s3.bucketsuffix", String.class);
    }


    public void write(Path file, String repo, String key) throws IOException {
        String bucket = repo + "." + bucketSuffix;
        try {
            amazonS3.putObject(bucket, key, file.toFile());
        } catch (AmazonS3Exception ex) {
            logger.error("S3Connection.putToS3: Cannot put " + file + " to " + bucket, ex);
            throw ex;
        }
    }

    public void write(String contents, String repo, String key) throws IOException {
        String bucket = repo + "." + bucketSuffix;
        try {
            amazonS3.putObject(bucket, key, contents);
        } catch (AmazonS3Exception ex) {
            logger.error("S3Connection.putToS3: Cannot put " + contents.replaceAll("\n", "|").substring(0, Math.min(50, contents.length())) + "... to " + bucket, ex);
            throw ex;
        }
    }

    public void write(byte[] contents, String repo, String key) throws IOException {
        String bucket = repo + "." + bucketSuffix;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contents.length);
        metadata.setContentType("application/zip");
        try {
            try (ByteArrayInputStream in = new ByteArrayInputStream(contents)) {
                amazonS3.putObject(bucket, key, in, metadata);
            }
        } catch (AmazonS3Exception ex) {
            String bytes = Arrays.toString(contents);
            logger.error("S3Connection.putToS3: Cannot put " + bytes.substring(0, Math.min(50, bytes.length())) + "... to " + bucket, ex);
            throw ex;
        }
    }

    public void delete(String repo, String key) throws IOException {
        String bucket = repo + "." + bucketSuffix;
        try {
            amazonS3.deleteObject(bucket, key);
        } catch (AmazonS3Exception ex) {
            logger.error("S3Connection.deleteFromS3: Cannot delete " + bucket, ex);
            throw ex;
        }
    }

    public byte[] read(String repo, String key) throws IOException {
        String bucket = repo + "." + bucketSuffix;

        byte[] bytes = null;
        try (InputStream in = amazonS3.getObject(bucket, key).getObjectContent()) {
            bytes = in.readAllBytes();
        } catch (AmazonS3Exception ex) {
            logger.error("S3Connection.readFromS3: Cannot read " + key + " from " + bucket, ex);
            throw ex;
        }
        return bytes;
    }
}
*/
class ProblemLocalConnection implements ProblemConnection {
    private Path root;
    private static final Logger logger = Logger.getLogger(ProblemLocalConnection.class);

    public ProblemLocalConnection(Config config) {
        this.root = Path.of(config.getValue("com.horstmann.codecheck.s3.local", String.class));
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            logger.error("Cannot create " + root, ex);
        }
    }

    @Override
    public void write(byte[] contents, String repo, String key) throws IOException {
        try {
            Path repoPath = root.resolve(repo);
            Files.createDirectories(repoPath);
            Path newFilePath = repoPath.resolve(key + ".zip");
            Files.write(newFilePath, contents);
        } catch (IOException ex) {
            String bytes = Arrays.toString(contents);
            logger.error("ProblemLocalConnection.write : Cannot put " + bytes.substring(0, Math.min(50, bytes.length())) + "... to " + repo, ex);
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
            logger.error("ProblemLocalConnection.delete : Cannot delete " + repo, ex);
            throw ex;
        }
    }

    @Override
    public byte[] read(String repo, String key) throws IOException {
        byte[] result;
        try {
            Path repoPath = root.resolve(repo);
            Path filePath = repoPath.resolve(key + ".zip");
            result = Files.readAllBytes(filePath);
        } catch (IOException ex) {
            logger.error("ProblemLocalConnection.read : Cannot read " + key + " from " + repo, ex);
            throw ex;
        }
        return result;
    }
}
