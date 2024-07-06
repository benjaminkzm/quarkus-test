package models;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class S3Connection {
    @ConfigProperty(name = "com.horstmann.codecheck.s3.accessKey")
    String s3AccessKey;

    @ConfigProperty(name = "com.horstmann.codecheck.s3.secretKey")
    String s3SecretKey;

    @ConfigProperty(name = "com.horstmann.codecheck.s3.region")
    String s3Region;

    @ConfigProperty(name = "com.horstmann.codecheck.s3.bucketsuffix")
    String bucketSuffix;

    private AmazonS3 amazonS3;
    private AmazonDynamoDB amazonDynamoDB;
    private static final Logger logger = Logger.getLogger(S3Connection.class);

    public static class OutOfOrderException extends RuntimeException {}

    @Inject
    public S3Connection() {
        // Initialize AWS clients
        amazonS3 = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(s3AccessKey, s3SecretKey)))
                .withRegion(s3Region)
                .withForceGlobalBucketAccessEnabled(true)
                .build();

        amazonDynamoDB = AmazonDynamoDBClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(s3AccessKey, s3SecretKey)))
                .withRegion("us-west-1")
                .build();
    }

    public boolean isOnS3(String repo) {
        String key = "com.horstmann.codecheck.repo." + repo;
        return !config.hasPath(key) || config.getString(key).isEmpty();
    }

    public boolean isOnS3(String repo, String key) {
        String bucket = repo + "." + bucketSuffix;
        return getS3Connection().doesObjectExist(bucket, key);
    }

    private AmazonS3 getS3Connection() { 
        return amazonS3; 
    }

    public AmazonDynamoDB getAmazonDynamoDB() {
        return amazonDynamoDB;
    }

    public void putToS3(Path file, String repo, String key)
            throws IOException {
        String bucket = repo + "." + bucketSuffix;
        try {
            getS3Connection().putObject(bucket, key, file.toFile());
        } catch (AmazonS3Exception ex) {
            logger.error("S3Connection.putToS3: Cannot put " + file + " to " + bucket);
            throw ex;
        }
    }

    public void putToS3(String contents, String repo, String key)
            throws IOException {
        String bucket = repo + "." + bucketSuffix;
        try {
            getS3Connection().putObject(bucket, key, contents);
        } catch (AmazonS3Exception ex) {
            logger.error("S3Connection.putToS3: Cannot put " + contents.replaceAll("\n", "|").substring(0, Math.min(50, contents.length())) + "... to " + bucket);
            throw ex;
        }
    }

    public void putToS3(byte[] contents, String repo, String key)
            throws IOException {
        String bucket = repo + "." + bucketSuffix;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contents.length);
        metadata.setContentType("application/zip");
        try (ByteArrayInputStream in = new ByteArrayInputStream(contents)) {
            getS3Connection().putObject(bucket, key, in, metadata);
        } catch (AmazonS3Exception ex) {
            String bytes = Arrays.toString(contents);
            logger.error("S3Connection.putToS3: Cannot put " + bytes.substring(0, Math.min(50, bytes.length())) + "... to " + bucket);
            throw ex;
        }
    }

    public void deleteFromS3(String repo, String key)
            throws IOException {
        String bucket = repo + "." + bucketSuffix;
        try {
            getS3Connection().deleteObject(bucket, key);
        } catch (AmazonS3Exception ex) {
            logger.error("S3Connection.deleteFromS3: Cannot delete " + bucket);
            throw ex;
        }
    }

    public byte[] readFromS3(String repo, String problem)
            throws IOException {
        String bucket = repo + "." + bucketSuffix;

        byte[] bytes = null;
        try (InputStream in = getS3Connection().getObject(bucket, problem).getObjectContent()) {
            bytes = in.readAllBytes();
        } catch (AmazonS3Exception ex) {
            logger.error("S3Connection.readFromS3: Cannot read " + problem + " from " + bucket);
            throw ex;
        }
        return bytes;
    }

    public List<String> readS3keys(String repo, String keyPrefix) throws AmazonServiceException {
        String bucket = repo + "." + bucketSuffix;
        ListObjectsV2Request req = new ListObjectsV2Request()
                .withBucketName(bucket).withMaxKeys(100).withPrefix(keyPrefix);
        ListObjectsV2Result result;
        List<String> allKeys = new ArrayList<>();

        do {
            result = getS3Connection().listObjectsV2(req);
            for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
                allKeys.add(objectSummary.getKey());
            }
            req.setContinuationToken(result.getNextContinuationToken());
        } while (result.isTruncated());
        return allKeys;
    }

    public ObjectNode readJsonObjectFromDynamoDB(String tableName, String primaryKeyName, String primaryKeyValue) throws IOException {
        String result = readJsonStringFromDynamoDB(tableName, primaryKeyName, primaryKeyValue);
        return result == null ? null : (ObjectNode)(new ObjectMapper().readTree(result));
    }

    public String readJsonStringFromDynamoDB(String tableName, String primaryKeyName, String primaryKeyValue) throws IOException {
        try {
            DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
            Table table = dynamoDB.getTable(tableName);
            ItemCollection<QueryOutcome> items = table.query(primaryKeyName, primaryKeyValue);
            Iterator<Item> iterator = items.iterator();
            return iterator.hasNext() ? iterator.next().toJSON() : null;
        } catch (ResourceNotFoundException ex) {
            return null;
        }
    }

    public ObjectNode readJsonObjectFromDynamoDB(String tableName, String primaryKeyName, String primaryKeyValue, String sortKeyName, String sortKeyValue) throws IOException {
        String result = readJsonStringFromDynamoDB(tableName, primaryKeyName, primaryKeyValue, sortKeyName, sortKeyValue);
        return result == null ? null : (ObjectNode)(new ObjectMapper().readTree(result));
    }

    public ObjectNode readNewestJsonObjectFromDynamoDB(String tableName, String primaryKeyName, String primaryKeyValue) {
        DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
        Table table = dynamoDB.getTable(tableName);
        QuerySpec spec = new QuerySpec()
                .withKeyConditionExpression(primaryKeyName + " = :primaryKey")
                .withValueMap(new ValueMap().withString(":primaryKey", primaryKeyValue))
                .withScanIndexForward(false);

        ItemCollection<QueryOutcome> items = table.query(spec);
        Iterator<Item> iterator = items.iterator();
        if (iterator.hasNext()) {
            String result = iterator.next().toJSON();
            try {
                return (ObjectNode)(new ObjectMapper().readTree(result));
            } catch (JsonProcessingException ex) {
                return null;
            }
        } else {
            return null;
        }
    }

    public String readJsonStringFromDynamoDB(String tableName, String primaryKeyName, String primaryKeyValue, String sortKeyName, String sortKeyValue) throws IOException {
        DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
        Table table = dynamoDB.getTable(tableName);
        ItemCollection<QueryOutcome> items = table.query(primaryKeyName, primaryKeyValue,
                new RangeKeyCondition(sortKeyName).eq(sortKeyValue));
        Iterator<Item> iterator = items.iterator();
        return iterator.hasNext() ? iterator.next().toJSON() : null;
    }

    public Map<String, ObjectNode> readJsonObjectsFromDynamoDB(String tableName, String primaryKeyName, String primaryKeyValue, String sortKeyName) throws IOException {
        DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
        Table table = dynamoDB.getTable(tableName);
        ItemCollection<QueryOutcome> items = table.query(primaryKeyName, primaryKeyValue);
        Iterator<Item> iterator = items.iterator();
        Map<String, ObjectNode> itemMap = new HashMap<>();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            String key = item.getString(sortKeyName);
            itemMap.put(key, (ObjectNode)(new ObjectMapper().readTree(item.toJSON())));
        }
        return itemMap;
    }

    public void writeJsonObjectToDynamoDB(String tableName, ObjectNode obj) {
        try {
            DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
            Table table = dynamoDB.getTable(tableName);
            table.putItem(
                new PutItemSpec()
                    .withItem(Item.fromJSON(obj.toString()))
            );
        } catch (IllegalArgumentException ex) {
            logger.warn("writeJsonObjectToDynamoDB caused the error message: " + ex.getMessage());
        }
    }

    public boolean writeNewerJsonObjectToDynamoDB(String tableName, ObjectNode obj, String primaryKeyName, String timeStampKeyName) {
        DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
        Table table = dynamoDB.getTable(tableName);
        String conditionalExpression = "attribute_not_exists(" + primaryKeyName + ") OR " + timeStampKeyName + " < :" + timeStampKeyName;
        try {
            table.putItem(
                new PutItemSpec()
                    .withItem(Item.fromJSON(obj.toString()))
                    .withConditionExpression(conditionalExpression)
                    .withValueMap(Collections.singletonMap(":" + timeStampKeyName, obj.get(timeStampKeyName).asText())));
            return true;
        } catch (ConditionalCheckFailedException e) {
            logger.warn("writeNewerJsonObjectToDynamoDB: " + e.getMessage() + " " + obj);
            return false;
        }
    }
}
