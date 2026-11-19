package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;
import ru.posts.publisher.service.RemoteActionProcessor;
import ru.posts.ratelimit.RateLimitDecision;
import ru.posts.ratelimit.SocialRateLimiter;

/** BE-33 acceptance: Telegram remote delete / unsupported restore / registry. */
@Tag("ci")
class Be33RemoteActionsTgIT extends AbstractPipelineIT {

    @Autowired
    private RemotePostRegistryJdbc remotePostRegistryJdbc;

    @Autowired
    private RemoteActionProcessor remoteActionProcessor;

    @Test
    @DisplayName("delete_comment via deleteMessage succeeds")
    void deleteCommentHappyPath() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        String chatId = "-1003391";
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, chatId);

        emulator()
                .put(
                        "/_control/posts",
                        """
                        {"items":[{"network":"telegram","channelId":"%s","remotePostId":"501","text":"cmt","createdAt":"%s"}]}
                        """
                                .formatted(chatId, Instant.now()));

        String key = "be33-tg-del-cmt-" + UuidV7.generate();
        String target = "{\"commentType\":\"channel_comment\",\"remoteCommentId\":\"501\",\"remotePostId\":\"501\"}";
        UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                jdbcTemplate, workspaceId, channelId, "delete_comment", target, "{}", key, "antispam");

        assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                .isEqualTo("succeeded"));
    }

    @Test
    @DisplayName("delete_post via deleteMessage succeeds")
    void deletePostHappyPath() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        String chatId = "-1003392";
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, chatId);

        emulator()
                .put(
                        "/_control/posts",
                        """
                        {"items":[{"network":"telegram","channelId":"%s","remotePostId":"502","text":"post","createdAt":"%s"}]}
                        """
                                .formatted(chatId, Instant.now()));

        String key = "be33-tg-del-post-" + UuidV7.generate();
        String target = "{\"remotePostId\":\"502\"}";
        UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                jdbcTemplate, workspaceId, channelId, "delete_post", target, "{}", key, "manual");

        assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                .isEqualTo("succeeded"));
    }

    @Test
    @DisplayName("restore_comment → failed_permanent (unsupported)")
    void restoreCommentUnsupported() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1003393");

        String key = "be33-tg-restore-" + UuidV7.generate();
        String target = "{\"commentType\":\"channel_comment\",\"remoteCommentId\":\"9\"}";
        UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                jdbcTemplate, workspaceId, channelId, "restore_comment", target, "{}", key, "manual");

        assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                .isEqualTo("failed_permanent"));
    }

    @Test
    @DisplayName("registry after TG publish: isOwn true")
    void registryAfterPublish() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1003394");
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(2),
                "be33-tg-registry");

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        String remotePostId = jdbcTemplate.queryForObject(
                "SELECT remote_post_id FROM publications WHERE id = ?", String.class, publicationId);
        assertThat(remotePostId).isNotBlank();
        assertThat(remotePostRegistryJdbc.isOwn(channelId, remotePostId)).isTrue();
    }

    @Test
    @DisplayName("ban_user via banChatMember succeeds")
    void banUserHappyPath() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        String chatId = "-1003396";
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, chatId);

        String key = "be33-tg-ban-" + UuidV7.generate();
        String target = "{\"remoteUserId\":\"424242\"}";
        UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                jdbcTemplate, workspaceId, channelId, "ban_user", target, "{}", key, "antispam");

        assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                .isEqualTo("succeeded"));

        String bans = emulator().get("/_control/bans");
        assertThat(bans).contains("424242");
    }

    @Test
    @DisplayName("rate limit: deny → failed_retryable/rate_limited then success")
    void rateLimitedThenSuccess() throws Exception {
        SocialRateLimiter original =
                (SocialRateLimiter) ReflectionTestUtils.getField(remoteActionProcessor, "rateLimiter");
        AtomicInteger calls = new AtomicInteger();
        SocialRateLimiter stub = (network, accountId, chatId) -> {
            if (calls.getAndIncrement() == 0) {
                return RateLimitDecision.deny(Duration.ofMillis(50));
            }
            return RateLimitDecision.allow();
        };
        ReflectionTestUtils.setField(remoteActionProcessor, "rateLimiter", stub);
        try {
            UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
            String chatId = "-1003395";
            UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, chatId);

            emulator()
                    .put(
                            "/_control/posts",
                            """
                            {"items":[{"network":"telegram","channelId":"%s","remotePostId":"503","text":"rl","createdAt":"%s"}]}
                            """
                                    .formatted(chatId, Instant.now()));

            String key = "be33-tg-rl-" + UuidV7.generate();
            String target = "{\"remotePostId\":\"503\"}";
            UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                    jdbcTemplate, workspaceId, channelId, "delete_post", target, "{}", key, "manual");

            assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                        .isEqualTo("failed_retryable");
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT error_code FROM social_remote_actions WHERE id = ?", String.class, actionId))
                        .isEqualTo("rate_limited");
            });

            jdbcTemplate.update(
                    "UPDATE social_remote_actions SET next_attempt_at = now() - interval '1 second' WHERE id = ?",
                    actionId);
            assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                    .isEqualTo("succeeded"));
        } finally {
            ReflectionTestUtils.setField(remoteActionProcessor, "rateLimiter", original);
        }
    }
}
