package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.posts.common.uuid.UuidV7;

/** TC-XP-10: crosspost publication pipeline UNKNOWN_OUTCOME → findExisting → 1 post in emulator. */
@Tag("ci")
class Qa14CrosspostReconcileIT extends AbstractVkPipelineIT {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("TC-XP-10: UNKNOWN_OUTCOME on crosspost publish → reconcile → exactly 1 emulator post")
    void crosspostUnknownOutcomeReconcilesToSinglePost() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", "-1003410", "group");

        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        String text = "crosspost reconcile " + UuidV7.generate();
        ObjectNode sourceRef = objectMapper.createObjectNode();
        sourceRef
                .putObject("root_origin")
                .put("network", "vk")
                .put("channel_id", channelId.toString())
                .put("remote_post_id", "src-9901");
        sourceRef.put("hop", 1);

        UUID publicationId = PipelineTestSupport.insertVkPublication(
                jdbcTemplate,
                workspaceId,
                postId,
                variantId,
                channelId,
                Instant.now().minusSeconds(5),
                text);
        jdbcTemplate.update(
                """
                UPDATE posts SET source_type = 'crosspost', source_ref = ?::jsonb
                WHERE id = ?
                """,
                sourceRef.toString(),
                postId);

        emulator()
                .putScenario(
                        """
                        {"networks":{"vk":{"forcedError":"drop"}}}
                        """);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isIn("succeeded", "failed_retryable"));

        if (!"succeeded"
                .equals(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))) {
            emulator().putScenario("{\"networks\":{\"vk\":{}}}");
            jdbcTemplate.update(
                    "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?",
                    publicationId);
            dispatchService.dispatchDueBatch();
            await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                    .isEqualTo("succeeded"));
        }

        long vkCount = objectMapper
                .readTree(emulator().get("/_control/posts/count"))
                .path("vk")
                .asLong();
        assertThat(vkCount).isEqualTo(1);
        ObjectNode verify = (ObjectNode) objectMapper.readTree(emulator().get("/_control/verify"));
        assertThat(verify.path("ok").asBoolean()).isTrue();
    }
}
