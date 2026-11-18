package ru.posts.publisher.pipeline;

import java.net.URI;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import ru.posts.testsupport.emulator.SocialEmulatorProcess;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * JVM-singleton containers + social-emulator for publisher pipeline ITs.
 *
 * <p>Postgres/Redis/emulator/S3 are shared. RabbitMQ is <strong>per publisher network</strong> so a
 * cached VK/OK Spring context cannot drain {@code publish.vk}/{@code publish.ok} while TG ITs
 * assert queue isolation (TC-PUB-11).
 *
 * <p>S3 mock stays singleton so VK ITs that need real object bytes (QA-13) share one Spring context
 * with StoryVk — a second {@code @DynamicPropertySource} would spin another listener on {@link
 * #RABBIT_VK} and steal publishes.
 */
final class SharedPipelineInfra {

    static final String S3_BUCKET = "posts-media";
    static final String S3_ACCESS_KEY = "posts";
    static final String S3_SECRET_KEY = "postsposts";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    /** Rabbit for {@code posts.publisher.network=tg} contexts only. */
    static final RabbitMQContainer RABBIT_TG =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    /** Isolated broker for VK story/publisher ITs. */
    static final RabbitMQContainer RABBIT_VK =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    /** Isolated broker for OK Moments/publisher ITs. */
    static final RabbitMQContainer RABBIT_OK =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    /** Isolated broker for Max publisher ITs (BE-28). */
    static final RabbitMQContainer RABBIT_MAX =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static final GenericContainer<?> S3MOCK = new GenericContainer<>(DockerImageName.parse("adobe/s3mock:3.12.0"))
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", S3_BUCKET)
            .withExposedPorts(9090)
            .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200));

    private static SocialEmulatorProcess emulator;
    private static final Object EMULATOR_LOCK = new Object();

    static {
        POSTGRES.start();
        RABBIT_TG.start();
        RABBIT_VK.start();
        RABBIT_OK.start();
        RABBIT_MAX.start();
        REDIS.start();
        S3MOCK.start();
    }

    private SharedPipelineInfra() {}

    static String s3Endpoint() {
        return "http://" + S3MOCK.getHost() + ":" + S3MOCK.getMappedPort(9090);
    }

    static void putS3Object(String key, byte[] bytes, String contentType) {
        try (S3Client client = s3Client()) {
            try {
                client.createBucket(
                        CreateBucketRequest.builder().bucket(S3_BUCKET).build());
            } catch (Exception ignored) {
                // bucket may already exist
            }
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(S3_BUCKET)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes));
        }
    }

    static S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(S3_ACCESS_KEY, S3_SECRET_KEY)))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    static SocialEmulatorProcess emulator() {
        synchronized (EMULATOR_LOCK) {
            if (emulator == null) {
                System.setProperty("posts.repo.root", findRepoRoot());
                try {
                    emulator = SocialEmulatorProcess.start();
                    Runtime.getRuntime().addShutdownHook(new Thread(emulator::close));
                } catch (Exception ex) {
                    throw new IllegalStateException("Failed to start social-emulator", ex);
                }
            }
            return emulator;
        }
    }

    static String findRepoRoot() {
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            if (java.nio.file.Files.isDirectory(dir.resolve("tests/social-emulator"))
                    && java.nio.file.Files.isDirectory(dir.resolve("backend"))) {
                return dir.toString();
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        java.nio.file.Path fromModule =
                java.nio.file.Path.of("../..").toAbsolutePath().normalize();
        if (java.nio.file.Files.isDirectory(fromModule.resolve("tests/social-emulator"))) {
            return fromModule.toString();
        }
        return java.nio.file.Path.of("").toAbsolutePath().toString();
    }
}
