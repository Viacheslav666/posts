package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import ru.posts.common.uuid.UuidV7;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * QA-13: story publish uses a real JPEG from S3 (not the 4-byte MediaPublishSupport stub). Emulator
 * contentSha256 must match the uploaded file.
 */
@Tag("ci")
class Qa13StoryRealMediaIT extends AbstractVkPipelineIT {

    /** Minimal JFIF JPEG (larger / richer than FF D8 FF D9 stub). */
    private static final byte[] REAL_JPEG = new byte[] {
        (byte) 0xFF,
        (byte) 0xD8,
        (byte) 0xFF,
        (byte) 0xE0,
        0x00,
        0x10,
        0x4A,
        0x46,
        0x49,
        0x46,
        0x00,
        0x01,
        0x01,
        0x00,
        0x00,
        0x01,
        0x00,
        0x01,
        0x00,
        0x00,
        (byte) 0xFF,
        (byte) 0xD9
    };

    private static final String STUB_SHA = "32461d5bd1773012acef0ba15636752949bd7c2ce50f9172159d9f56cf0dd9af";

    /** Started eagerly (same pattern as SharedPipelineInfra) so DynamicPropertySource sees ports. */
    static final GenericContainer<?> S3MOCK = new GenericContainer<>(DockerImageName.parse("adobe/s3mock:3.12.0"))
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", "posts-media")
            .withExposedPorts(9090)
            .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200));

    static {
        S3MOCK.start();
    }

    @DynamicPropertySource
    static void s3Props(DynamicPropertyRegistry registry) {
        registry.add("posts.s3.enabled", () -> "true");
        registry.add("posts.s3.endpoint", () -> "http://" + S3MOCK.getHost() + ":" + S3MOCK.getMappedPort(9090));
        registry.add("posts.s3.bucket", () -> "posts-media");
        registry.add("posts.s3.access-key", () -> "posts");
        registry.add("posts.s3.secret-key", () -> "postsposts");
        registry.add("posts.s3.path-style-access", () -> "true");
        registry.add("posts.s3.region", () -> "us-east-1");
    }

    @Autowired
    private ObjectMapper objectMapper;

    private String realSha;

    @BeforeEach
    void putRealJpeg() throws Exception {
        realSha = sha256Hex(REAL_JPEG);
        assertThat(realSha).isNotEqualTo(STUB_SHA);
        try (S3Client client = s3Client()) {
            try {
                client.createBucket(
                        CreateBucketRequest.builder().bucket("posts-media").build());
            } catch (Exception ignored) {
                // bucket may already exist
            }
        }
    }

    @Test
    @DisplayName("QA-13: VK story publishes real JPEG bytes from S3")
    void storyPublishesRealJpegFile() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", "910", "group");
        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        UUID mediaId = UuidV7.generate();
        String s3Key = "workspaces/" + workspaceId + "/media/" + mediaId + "/story-real.jpg";

        try (S3Client client = s3Client()) {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket("posts-media")
                            .key(s3Key)
                            .contentType("image/jpeg")
                            .build(),
                    RequestBody.fromBytes(REAL_JPEG));
        }

        jdbcTemplate.update(
                """
                INSERT INTO media_assets (
                    id, workspace_id, kind, s3_key, mime, size_bytes, width, height, sha256, status,
                    derivatives, meta, filename)
                VALUES (?, ?, 'image', ?, 'image/jpeg', ?, 1080, 1920, ?, 'ready',
                    '{}'::jsonb,
                    '{"width":1080,"height":1920,"sizeBytes":22,"codec":"jpeg"}'::jsonb,
                    'story-real.jpg')
                """,
                mediaId,
                workspaceId,
                s3Key,
                REAL_JPEG.length,
                realSha);

        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    id, workspace_id, title, base_content, status, content_kind, story_ttl, story_options)
                VALUES (?, ?, 'qa13-story-real', ?::jsonb, 'scheduled', 'story', 'ttl_24h',
                    '{"mediaKind":"photo","addToNews":true}'::jsonb)
                """,
                postId,
                workspaceId,
                "{\"text\":\"qa13 real story\",\"mediaIds\":[\"" + mediaId + "\"],\"mediaKind\":\"photo\"}");
        jdbcTemplate.update(
                """
                INSERT INTO post_variants (id, post_id, channel_id, network, content, options)
                VALUES (?, ?, ?, 'vk', ?::jsonb, '{"addToNews":true}'::jsonb)
                """,
                variantId,
                postId,
                channelId,
                "{\"text\":\"qa13 real story\",\"mediaKind\":\"photo\",\"mediaIds\":[\"" + mediaId + "\"]}");

        UUID publicationId = UuidV7.generate();
        String key = PipelineTestSupport.sha256(variantId + "|" + Instant.now().minusSeconds(2) + "|story");
        Instant runAt = Instant.now().minusSeconds(2);
        jdbcTemplate.update(
                """
                INSERT INTO publications (
                    id, workspace_id, post_id, variant_id, channel_id, network,
                    run_at, idempotency_key, state)
                VALUES (?, ?, ?, ?, ?, 'vk', ?, ?, 'pending')
                """,
                publicationId,
                workspaceId,
                postId,
                variantId,
                channelId,
                java.sql.Timestamp.from(runAt),
                key);

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        JsonNode posts = objectMapper.readTree(emulator().get("/_control/posts?network=vk&contentType=story"));
        assertThat(posts).hasSize(1);
        String contentSha = posts.get(0).path("metadata").path("contentSha256").asText();
        assertThat(contentSha)
                .as("emulator must receive real JPEG bytes, not MediaPublishSupport stub")
                .isEqualTo(realSha)
                .isNotEqualTo(STUB_SHA);
        assertThat(posts.get(0).path("metadata").path("contentSize").asInt()).isEqualTo(REAL_JPEG.length);
    }

    private static S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(java.net.URI.create("http://" + S3MOCK.getHost() + ":" + S3MOCK.getMappedPort(9090)))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("posts", "postsposts")))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        return HexFormat.of().formatHex(digest);
    }
}
