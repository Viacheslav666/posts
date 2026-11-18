package ru.posts.common.s3;

import java.time.Duration;
import java.util.Objects;

/** Plain S3 settings shared by API and media worker (no Spring). */
public record S3Settings(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess,
        Duration presignTtl) {

    public S3Settings {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(accessKey, "accessKey");
        Objects.requireNonNull(secretKey, "secretKey");
        if (presignTtl == null) {
            presignTtl = Duration.ofMinutes(15);
        }
    }

    public boolean hasCustomEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }
}
