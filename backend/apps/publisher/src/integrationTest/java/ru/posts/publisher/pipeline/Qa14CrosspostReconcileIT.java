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

        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    id, workspace_id, title, base_content, status, content_kind, story_options,
                    source_type, source_ref)
                VALUES (?, ?, 'xp', ?::jsonb, 'scheduled', 'post', '{}'::jsonb, 'crosspost', ?::jsonb)
                """,
                postId,
                workspaceId,
                "{\"text\":\"" + text + "\"}",
                sourceRef.toString());
        jdbcTemplate.update(
                """
                INSERT INTO post_variants (id, post_id, channel_id, network, content, options)
                VALUES (?, ?, ?, 'vk', ?::jsonb, '{}'::jsonb)
                """,
                variantId,
                postId,
                channelId,
                "{\"text\":\"" + text + "\"}");

        UUID publicationId = UuidV7.generate();
        String idem = "xp:" + UuidV7.generate() + ":" + channelId + ":src-9901:" + channelId;
        jdbcTemplate.update(
                """
                INSERT INTO publications (
                    id, workspace_id, post_id, variant_id, channel_id, network,
                    run_at, idempotency_key, state, attempts)
                VALUES (?, ?, ?, ?, ?, 'vk', ?, ?, 'pending', 0)
                """,
                publicationId,
                workspaceId,
                postId,
                variantId,
                channelId,
                java.sql.Timestamp.from(Instant.now().minusSeconds(10)),
                idem);

        emulator()
                .putScenario(
                        """
                        {"networks":{"vk":{"forcedError":"drop"}}}
                        """);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isIn("failed_retryable", "succeeded"));

        emulator().putScenario("{\"networks\":{\"vk\":{}}}");
        jdbcTemplate.update(
                """
                UPDATE publications
                SET state = 'failed_retryable', next_attempt_at = now() - interval '1 second'
                WHERE id = ? AND state <> 'succeeded'
                """,
                publicationId);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        long vkCount = objectMapper
                .readTree(emulator().get("/_control/posts/count"))
                .path("vk")
                .asLong();
        assertThat(vkCount).isEqualTo(1);
        ObjectNode verify = (ObjectNode) objectMapper.readTree(emulator().get("/_control/verify"));
        assertThat(verify.path("ok").asBoolean()).isTrue();
    }
}
