package ru.posts.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.posts.common.uuid.UuidV7;
import ru.posts.media.config.S3Properties;
import ru.posts.media.service.MediaProcessor;
import ru.posts.persistence.entity.MediaAssetEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.MediaAssetRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.UserRepository;
import ru.posts.persistence.repository.WorkspaceRepository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class MediaProcessorIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Container
    static GenericContainer<?> s3mock = new GenericContainer<>(DockerImageName.parse("adobe/s3mock:3.12.0"))
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", "posts-media")
            .withExposedPorts(9090)
            .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200));

    @DynamicPropertySource
    static void s3Props(DynamicPropertyRegistry registry) {
        registry.add("posts.s3.endpoint", () -> "http://" + s3mock.getHost() + ":" + s3mock.getMappedPort(9090));
        registry.add("posts.s3.bucket", () -> "posts-media");
        registry.add("posts.s3.access-key", () -> "posts");
        registry.add("posts.s3.secret-key", () -> "postsposts");
        registry.add("posts.s3.path-style-access", () -> "true");
        registry.add("posts.s3.region", () -> "us-east-1");
        registry.add("posts.media.clamav.enabled", () -> "false");
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }

    @Autowired
    MediaProcessor mediaProcessor;

    @Autowired
    MediaAssetRepository mediaAssetRepository;

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    S3Client s3Client;

    @Autowired
    S3Properties s3Properties;

    UUID workspaceId;

    @BeforeEach
    void setUp() {
        ensureBucket();
        PlanEntity plan = planRepository.findByCode("free").orElseThrow();
        UserEntity user = new UserEntity();
        user.setEmail("media-it-" + UUID.randomUUID() + "@example.com");
        user.setName("Media IT");
        user.setPasswordHash("x");
        user.setEmailVerifiedAt(Instant.now());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user = userRepository.save(user);

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setName("Media IT " + UUID.randomUUID());
        workspace.setOwnerUserId(user.getId());
        workspace.setPlanId(plan.getId());
        workspace.setBillingStatus("trial");
        workspace.setCreatedAt(Instant.now());
        workspace.setUpdatedAt(Instant.now());
        workspace = workspaceRepository.save(workspace);
        workspaceId = workspace.getId();
    }

    @Test
    void photoProcessingProducesReadyAndDimensions() throws Exception {
        byte[] jpeg = tinyJpeg();
        MediaAssetEntity asset = seedAsset("image", "image/jpeg", "photo.jpg", jpeg);
        assertThat(mediaProcessor.process(asset.getId())).isTrue();

        MediaAssetEntity ready = mediaAssetRepository.findById(asset.getId()).orElseThrow();
        assertThat(ready.getStatus()).isEqualTo("ready");
        assertThat(ready.getWidth()).isPositive();
        assertThat(ready.getHeight()).isPositive();
        assertThat(ready.getDerivatives()).containsKey("preview");
        assertThat(ready.getSha256()).isNotBlank();
    }

    @Test
    void videoProcessingWithSampleMp4() throws Exception {
        Path mp4 = Files.createTempFile("sample", ".mp4");
        try {
            Process ffmpeg = new ProcessBuilder(
                            "ffmpeg",
                            "-y",
                            "-f",
                            "lavfi",
                            "-i",
                            "color=c=blue:s=320x240:d=1",
                            "-f",
                            "lavfi",
                            "-i",
                            "sine=f=440:d=1",
                            "-shortest",
                            "-c:v",
                            "libx264",
                            "-pix_fmt",
                            "yuv420p",
                            "-c:a",
                            "aac",
                            mp4.toString())
                    .redirectErrorStream(true)
                    .start();
            assertThat(ffmpeg.waitFor(60, TimeUnit.SECONDS)).isTrue();
            assertThat(ffmpeg.exitValue()).isZero();
            byte[] bytes = Files.readAllBytes(mp4);
            MediaAssetEntity asset = seedAsset("video", "video/mp4", "clip.mp4", bytes);
            assertThat(mediaProcessor.process(asset.getId())).isTrue();
            MediaAssetEntity ready =
                    mediaAssetRepository.findById(asset.getId()).orElseThrow();
            assertThat(ready.getStatus()).isEqualTo("ready");
            assertThat(ready.getDerivatives()).containsKeys("mp4_16x9", "mp4_9x16", "thumbnail");
        } finally {
            Files.deleteIfExists(mp4);
        }
    }

    @Test
    void mimeSpoofingRejected() throws Exception {
        byte[] jpeg = tinyJpeg();
        MediaAssetEntity asset = seedAsset("video", "video/mp4", "spoof.mp4", jpeg);
        assertThat(mediaProcessor.process(asset.getId())).isTrue();
        MediaAssetEntity failed = mediaAssetRepository.findById(asset.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo("failed");
        assertThat(failed.getErrorMessage()).containsIgnoringCase("MIME");
    }

    @Test
    void redeliveryIsIdempotent() throws Exception {
        byte[] jpeg = tinyJpeg();
        MediaAssetEntity asset = seedAsset("image", "image/jpeg", "idem.jpg", jpeg);
        assertThat(mediaProcessor.process(asset.getId())).isTrue();
        MediaAssetEntity first = mediaAssetRepository.findById(asset.getId()).orElseThrow();
        Map<String, Object> derivatives = Map.copyOf(first.getDerivatives());
        assertThat(mediaProcessor.process(asset.getId())).isTrue();
        MediaAssetEntity second = mediaAssetRepository.findById(asset.getId()).orElseThrow();
        assertThat(second.getStatus()).isEqualTo("ready");
        assertThat(second.getDerivatives()).isEqualTo(derivatives);
    }

    private MediaAssetEntity seedAsset(String kind, String mime, String filename, byte[] bytes) {
        UUID id = UuidV7.generate();
        String key = "workspaces/" + workspaceId + "/media/" + id + "/" + filename;
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(key)
                        .contentType(mime)
                        .build(),
                RequestBody.fromBytes(bytes));

        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setId(id);
        asset.setWorkspaceId(workspaceId);
        asset.setKind(kind);
        asset.setMime(mime);
        asset.setFilename(filename);
        asset.setS3Key(key);
        asset.setSizeBytes((long) bytes.length);
        asset.setStatus("processing");
        asset.setDerivatives(Map.of());
        asset.setTags(java.util.List.of());
        asset.setCreatedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        return mediaAssetRepository.save(asset);
    }

    private void ensureBucket() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .build());
        } catch (Exception ignored) {
            // adobe/s3mock may pre-create via env
        }
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(s3Properties.getBucket()).isNotBlank());
    }

    /** Minimal valid JPEG (1x1). */
    private static byte[] tinyJpeg() throws Exception {
        Path tmp = Files.createTempFile("tiny", ".jpg");
        try {
            Process p = new ProcessBuilder(
                            "ffmpeg",
                            "-y",
                            "-f",
                            "lavfi",
                            "-i",
                            "color=c=red:s=2x2:d=0.1",
                            "-frames:v",
                            "1",
                            tmp.toString())
                    .redirectErrorStream(true)
                    .start();
            assertThat(p.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(p.exitValue()).isZero();
            return Files.readAllBytes(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
