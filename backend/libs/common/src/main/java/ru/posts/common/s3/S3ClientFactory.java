package ru.posts.common.s3;

import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Builds AWS SDK v2 S3 clients for MinIO / s3mock / Yandex Object Storage. */
public final class S3ClientFactory {

    private S3ClientFactory() {}

    public static S3Client createClient(S3Settings settings) {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(settings.accessKey(), settings.secretKey()));
        var s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(settings.pathStyleAccess())
                .build();
        var builder = S3Client.builder()
                .region(Region.of(settings.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config);
        if (settings.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(settings.endpoint()));
        }
        return builder.build();
    }

    public static S3Presigner createPresigner(S3Settings settings) {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(settings.accessKey(), settings.secretKey()));
        var s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(settings.pathStyleAccess())
                .build();
        var builder = S3Presigner.builder()
                .region(Region.of(settings.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config);
        if (settings.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(settings.endpoint()));
        }
        return builder.build();
    }
}
