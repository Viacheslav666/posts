package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import ru.posts.common.uuid.UuidV7;

/** QA-11: OK Moments pipeline against social-emulator. */
@Tag("ci")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StoryOkPipelineIT extends AbstractOkPipelineIT {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("TC-STORY-02: OK Moments video path publishes once")
    void tcStory02OkMomentPublish() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "ok", "42", "group");
        UUID publicationId = PipelineTestSupport.insertStoryPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                "ok",
                Instant.now().minusSeconds(2),
                "story-ok-02",
                "ttl_24h");

        // Force video mediaKind via variant content.
        jdbcTemplate.update(
                """
                UPDATE post_variants
                SET content = jsonb_set(content, '{mediaKind}', '"video"')
                WHERE id = (SELECT variant_id FROM publications WHERE id = ?)
                """,
                publicationId);

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        JsonNode posts = objectMapper.readTree(emulator().get("/_control/posts?network=ok&contentType=story"));
        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).path("metadata").path("contentType").asText()).isEqualTo("story");
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/verify"))
                        .path("ok")
                        .asBoolean())
                .isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("TC-STORY-03: OK effective_ttl=ttl_24h stored in publication meta")
    void tcStory03OkEffectiveTtl() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "ok", "43", "group");
        UUID publicationId = PipelineTestSupport.insertStoryPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                "ok",
                Instant.now().minusSeconds(2),
                "story-ok-03",
                "ttl_48h");

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                    .isEqualTo("succeeded");
            String meta = jdbcTemplate.queryForObject(
                    "SELECT meta::text FROM publications WHERE id = ?", String.class, publicationId);
            assertThat(meta).contains("effective_ttl");
            assertThat(meta).contains("ttl_24h");
        });
    }

    @Test
    @Order(3)
    @DisplayName("TC-STORY-15 OK: duplicate delivery → 1 moment")
    void tcStory15OkNoDuplicate() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "ok", "44", "group");
        UUID publicationId = PipelineTestSupport.insertStoryPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                "ok",
                Instant.now().minusSeconds(2),
                "story-ok-15",
                "ttl_24h");

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        jdbcTemplate.update(
                """
                UPDATE publications
                SET state = 'pending',
                    remote_post_id = NULL,
                    next_attempt_at = now() - interval '1 second',
                    locked_by = NULL,
                    locked_until = NULL
                WHERE id = ?
                """,
                publicationId);
        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        JsonNode posts = objectMapper.readTree(emulator().get("/_control/posts?network=ok&contentType=story"));
        assertThat(posts).hasSize(1);
    }
}
