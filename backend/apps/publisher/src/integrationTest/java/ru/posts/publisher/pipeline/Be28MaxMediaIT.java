package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import ru.posts.common.uuid.UuidV7;
import ru.posts.publisher.PublisherApplication;
import ru.posts.publisher.service.PublicationProcessor;
import ru.posts.testsupport.emulator.SocialEmulatorProcess;

/** BE-28: Max media publish against social-emulator (network=max). */
@Tag("ci")
@SpringBootTest(classes = PublisherApplication.class)
class Be28MaxMediaIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = SharedPipelineInfra.POSTGRES;

    @ServiceConnection
    static final RabbitMQContainer RABBIT = SharedPipelineInfra.RABBIT_MAX;

    static final GenericContainer<?> REDIS = SharedPipelineInfra.REDIS;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TestDispatchHelper dispatchService;

    @Autowired
    PublicationProcessor publicationProcessor;

    @Autowired
    ObjectMapper objectMapper;

    private static SocialEmulatorProcess emulator() {
        return SharedPipelineInfra.emulator();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("posts.ratelimit.redis-uri", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        registry.add("posts.publisher.network", () -> "max");
        registry.add("posts.publisher.pod-name", () -> "publisher-max-it");
        registry.add("posts.social.max.base-url", () -> emulator().baseUrl());
        registry.add("posts.social.max.read-timeout", () -> "5s");
        registry.add("posts.ratelimit.enabled", () -> "true");
        registry.add("posts.publisher.prefetch", () -> "200");
        registry.add("posts.publisher.publish-timeout", () -> "30s");
        registry.add("server.port", () -> "0");
    }

    @org.junit.jupiter.api.BeforeEach
    void reset() throws Exception {
        emulator().reset();
    }

    @Test
    @DisplayName("BE-28: Max publish with uploaded media attachment")
    void maxPublishWithMedia() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "max", "max-chat-28", "channel");

        UUID mediaId = UuidV7.generate();
        jdbcTemplate.update(
                """
                INSERT INTO media_assets (id, workspace_id, kind, s3_key, mime, size_bytes, sha256, status)
                VALUES (?, ?, 'image', ?, 'image/jpeg', 4, ?, 'ready')
                """,
                mediaId,
                workspaceId,
                "it/be28-max/" + mediaId + ".jpg",
                "sha-max-" + mediaId);

        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        Instant runAt = Instant.now().minusSeconds(2);
        jdbcTemplate.update(
                """
                INSERT INTO posts (id, workspace_id, title, base_content, status, content_kind, story_options)
                VALUES (?, ?, 'be28-max', ?::jsonb, 'scheduled', 'post', '{}'::jsonb)
                """,
                postId,
                workspaceId,
                "{\"text\":\"max media\",\"mediaIds\":[\"" + mediaId + "\"]}");
        jdbcTemplate.update(
                """
                INSERT INTO post_variants (id, post_id, channel_id, network, content, options)
                VALUES (?, ?, ?, 'max', ?::jsonb, '{"mute":true,"buttons":[{"text":"Open","url":"https://example.com"}]}'::jsonb)
                """,
                variantId,
                postId,
                channelId,
                "{\"text\":\"max media\",\"mediaIds\":[\"" + mediaId + "\"]}");

        UUID publicationId = UuidV7.generate();
        String key = PipelineTestSupport.sha256(variantId + "|" + runAt + "|max");
        jdbcTemplate.update(
                """
                INSERT INTO publications (
                    id, workspace_id, post_id, variant_id, channel_id, network,
                    run_at, idempotency_key, state)
                VALUES (?, ?, ?, ?, ?, 'max', ?, ?, 'pending')
                """,
                publicationId,
                workspaceId,
                postId,
                variantId,
                channelId,
                java.sql.Timestamp.from(runAt),
                key);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        JsonNode posts = objectMapper.readTree(emulator().get("/_control/posts?network=max"));
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts.get(0).path("metadata").path("attachment_count").asInt())
                .isGreaterThanOrEqualTo(1);
        assertThat(posts.get(0).path("metadata").path("notify").asBoolean(true)).isFalse();
    }
}
