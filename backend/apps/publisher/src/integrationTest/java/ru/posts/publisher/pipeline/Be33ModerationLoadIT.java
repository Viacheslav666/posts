package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.posts.common.uuid.UuidV7;

/**
 * BE-33 moderation load smoke: 100 remote actions complete without permanent failures.
 *
 * <p>Full 500/s sustained load is covered by unit rate-limiter tests ({@code
 * Bucket4jSocialRateLimiterAimdTest}); this IT only checks queue drain under moderate burst.
 */
@Tag("ci")
@Tag("slow")
class Be33ModerationLoadIT extends AbstractVkPipelineIT {

    private static final int ACTION_COUNT = 100;

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    @DisplayName("enqueue 100 delete_comment → dispatch → all succeeded, 0 permanent")
    void hundredActionsDrainWithoutPermanentFailures() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        String ownerId = "-1003500";
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", ownerId, "group");

        StringBuilder commentsJson = new StringBuilder("{\"items\":[");
        List<UUID> actionIds = new ArrayList<>(ACTION_COUNT);
        for (int i = 0; i < ACTION_COUNT; i++) {
            if (i > 0) {
                commentsJson.append(',');
            }
            String commentId = String.valueOf(5000 + i);
            commentsJson
                    .append("{\"network\":\"vk\",\"ownerId\":\"")
                    .append(ownerId)
                    .append("\",\"commentId\":\"")
                    .append(commentId)
                    .append("\",\"type\":\"wall\"}");
            String target = "{\"commentType\":\"wall\",\"remoteCommentId\":\"%s\",\"ownerId\":\"%s\"}"
                    .formatted(commentId, ownerId);
            actionIds.add(PipelineTestSupport.enqueueRemoteAction(
                    jdbcTemplate,
                    workspaceId,
                    channelId,
                    "delete_comment",
                    target,
                    "{}",
                    "be33-load-" + i + "-" + UuidV7.generate(),
                    "antispam"));
        }
        commentsJson.append("]}");
        emulator().put("/_control/comments", commentsJson.toString());

        // Initial enqueue + keep pumping retries (rate limiter may defer some attempts).
        // Sustained 500/s is covered by Bucket4jSocialRateLimiterAimdTest, not this smoke.
        await().pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofMinutes(2))
                .untilAsserted(() -> {
                    dispatchService.dispatchDueModerationActions();
                    Integer succeeded = jdbcTemplate.queryForObject(
                            """
                            SELECT count(*) FROM social_remote_actions
                            WHERE channel_id = ? AND state = 'succeeded'
                            """,
                            Integer.class,
                            channelId);
                    Integer permanent = jdbcTemplate.queryForObject(
                            """
                            SELECT count(*) FROM social_remote_actions
                            WHERE channel_id = ? AND state = 'failed_permanent'
                            """,
                            Integer.class,
                            channelId);
                    assertThat(permanent).isZero();
                    assertThat(succeeded).isEqualTo(ACTION_COUNT);
                });

        assertThat(actionIds).hasSize(ACTION_COUNT);
    }
}
