package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import ru.posts.testsupport.emulator.SocialEmulatorProcess;

/** BE-33 acceptance: Max delete_comment + access.denied permanent. */
@Tag("ci")
@SpringBootTest(classes = PublisherApplication.class)
class Be33RemoteActionsMaxIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = SharedPipelineInfra.POSTGRES;

    @ServiceConnection
    static final RabbitMQContainer RABBIT = SharedPipelineInfra.RABBIT_MAX;

    static final GenericContainer<?> REDIS = SharedPipelineInfra.REDIS;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TestDispatchHelper dispatchService;

    private static SocialEmulatorProcess emulator() {
        return SharedPipelineInfra.emulator();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("posts.ratelimit.redis-uri", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        registry.add("posts.publisher.network", () -> "max");
        registry.add("posts.publisher.pod-name", () -> "publisher-max-be33-it");
        registry.add("posts.social.max.base-url", () -> emulator().baseUrl());
        registry.add("posts.social.max.read-timeout", () -> "5s");
        registry.add("posts.ratelimit.enabled", () -> "true");
        registry.add("posts.publisher.prefetch", () -> "200");
        registry.add("posts.publisher.publish-timeout", () -> "30s");
        registry.add("server.port", () -> "0");
    }

    @BeforeEach
    void reset() throws Exception {
        emulator().reset();
    }

    @Test
    @DisplayName("Max delete_comment happy path")
    void deleteCommentHappyPath() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "max", "max-chat-be33", "channel");

        // Adapter omits chat_id → emulator keys comment by messageId; seed ownerId = message id.
        emulator()
                .put(
                        "/_control/comments",
                        """
                        {"items":[{"network":"max","ownerId":"mid-be33","commentId":"c-be33","type":"channel_comment"}]}
                        """);

        String key = "be33-max-del-cmt-" + UuidV7.generate();
        String target =
                """
                {"commentType":"channel_comment","remotePostId":"mid-be33","remoteCommentId":"c-be33"}
                """;
        UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                jdbcTemplate, workspaceId, channelId, "delete_comment", target, "{}", key, "antispam");

        assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                .isEqualTo("succeeded"));
    }

    @Test
    @DisplayName("Max ban_user via DELETE chat members")
    void banUserHappyPath() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId =
                PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "max", "max-chat-be33-ban", "channel");

        String key = "be33-max-ban-" + UuidV7.generate();
        String target = "{\"remoteUserId\":\"user-77\",\"ownerId\":\"max-chat-be33-ban\"}";
        UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                jdbcTemplate, workspaceId, channelId, "ban_user", target, "{}", key, "antispam");

        assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                .isEqualTo("succeeded"));

        String bans = emulator().get("/_control/bans");
        assertThat(bans).contains("user-77");
    }

    @Test
    @DisplayName("Max reply_comment via POST comments")
    void replyCommentHappyPath() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId =
                PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "max", "max-chat-be33-reply", "channel");

        String key = "be33-max-reply-" + UuidV7.generate();
        String target =
                """
                {"commentType":"channel_comment","remotePostId":"mid-reply","remoteCommentId":"c-parent"}
                """;
        String payload = "{\"text\":\"thanks\"}";
        UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                jdbcTemplate, workspaceId, channelId, "reply_comment", target, payload, key, "manual");

        assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                .isEqualTo("succeeded"));
    }

    @Test
    @DisplayName("Max access.denied → failed_permanent/permission")
    void accessDeniedPermanent() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId =
                PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "max", "max-chat-be33-deny", "channel");

        emulator().putScenario("{\"networks\":{\"max\":{\"forcedError\":\"access.denied\"}}}");
        try {
            String key = "be33-max-denied-" + UuidV7.generate();
            String target =
                    """
                    {"commentType":"channel_comment","remotePostId":"mid-deny","remoteCommentId":"c-deny"}
                    """;
            UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                    jdbcTemplate, workspaceId, channelId, "delete_comment", target, "{}", key, "manual");

            assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                        .isEqualTo("failed_permanent");
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT error_code FROM social_remote_actions WHERE id = ?", String.class, actionId))
                        .isEqualTo("permission");
            });
        } finally {
            emulator().putScenario("{\"networks\":{\"max\":{}}}");
        }
    }
}
