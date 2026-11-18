package ru.posts.publisher.media;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.posts.common.s3.S3Settings;

@ConfigurationProperties(prefix = "posts.s3")
public class PublisherS3Properties {

    private boolean enabled = false;
    private String endpoint = "";
    private String region = "ru-central1";
    private String bucket = "posts-media";
    private String accessKey = "posts";
    private String secretKey = "posts";
    private boolean pathStyleAccess = true;
    private Duration presignTtl = Duration.ofMinutes(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public Duration getPresignTtl() {
        return presignTtl;
    }

    public void setPresignTtl(Duration presignTtl) {
        this.presignTtl = presignTtl;
    }

    public S3Settings toSettings() {
        return new S3Settings(endpoint, region, bucket, accessKey, secretKey, pathStyleAccess, presignTtl);
    }
}
