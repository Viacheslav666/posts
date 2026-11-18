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

/**
 * QA-11: VK stories pipeline against social-emulator — publish, delete_story, 0 duplicates,
 * waiting_for_channel, ads fail-closed.
 */
@Tag("ci")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StoryVkPipelineIT extends AbstractVkPipelineIT {

    private static final String ERID = "test-erid-story-vk-0001";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("TC-STORY-01: VK photo story publishes once; remote_story_id set")
    void tcStory01VkPhotoPublish() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", "100", "group");
        UUID publicationId = PipelineTestSupport.insertStoryPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                "vk",
                Instant.now().minusSeconds(2),
                "story-vk-01",
                "ttl_24h");

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                    .isEqualTo("succeeded");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT remote_post_id FROM publications WHERE id = ?", String.class, publicationId))
                    .isNotBlank();
        });

        assertStoryCount("vk", 1);
        JsonNode posts = objectMapper.readTree(emulator().get("/_control/posts?network=vk&contentType=story"));
        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).path("metadata").path("contentType").asText()).isEqualTo("story");
        // BE-24: emulator received deterministic photo stub bytes from MediaPublishSupport (no S3 in IT).
        assertThat(posts.get(0).path("metadata").path("contentSha256").asText())
                .isEqualTo("32461d5bd1773012acef0ba15636752949bd7c2ce50f9172159d9f56cf0dd9af");
        assertThat(posts.get(0).path("metadata").path("contentSize").asInt()).isEqualTo(4);
    }

    @Test
    @Order(2)
    @DisplayName("TC-STORY-15: redelivery / worker restart → still 1 story in emulator")
    void tcStory15NoDuplicateOnRedelivery() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", "101", "group");
        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        UUID publicationId = PipelineTestSupport.insertStoryPublication(
                jdbcTemplate,
                workspaceId,
                postId,
                variantId,
                channelId,
                "vk",
                Instant.now().minusSeconds(2),
                "story-vk-15",
                "ttl_24h");

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        // Simulate lost ack / redelivery: reset to pending with same idempotency key and bump attempts.
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

        assertStoryCount("vk", 1);
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/verify"))
                        .path("ok")
                        .asBoolean())
                .isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("TC-STORY-09: delete_story action after publish; re-dispatch idempotent")
    void tcStory09DeleteStoryAction() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", "102", "group");
        Instant deleteAfter = Instant.now().plusSeconds(7200);
        UUID publicationId = PipelineTestSupport.insertStoryPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                "vk",
                Instant.now().minusSeconds(2),
                "story-vk-09",
                "ttl_24h",
                deleteAfter,
                false,
                null);

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        Integer deleteActions = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM scheduled_actions
                WHERE publication_id = ? AND kind = 'delete_story'
                """,
                Integer.class,
                publicationId);
        assertThat(deleteActions).isEqualTo(1);

        jdbcTemplate.update(
                """
                UPDATE scheduled_actions
                SET run_at = now() - interval '1 second'
                WHERE publication_id = ? AND kind = 'delete_story'
                """,
                publicationId);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            dispatchService.dispatchDueActions();
            assertThat(jdbcTemplate.queryForObject(
                            """
                            SELECT state FROM scheduled_actions
                            WHERE publication_id = ? AND kind = 'delete_story'
                            """,
                            String.class,
                            publicationId))
                    .isEqualTo("succeeded");
        });

        assertThat(dispatchService.dispatchDueActions()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT state FROM scheduled_actions
                        WHERE publication_id = ? AND kind = 'delete_story'
                        """,
                        String.class,
                        publicationId))
                .isEqualTo("succeeded");
    }

    @Test
    @Order(4)
    @DisplayName("TC-STORY-24: ad story without erid never reaches network (fail-closed)")
    void tcStory24AdFailClosedWithoutErid() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", "103", "group");
        UUID publicationId = PipelineTestSupport.insertStoryPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                "vk",
                Instant.now().minusSeconds(2),
                "story-ad-no-erid",
                "ttl_24h",
                null,
                true,
                null);

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("failed_permanent"));

        assertStoryCount("vk", 0);
    }

    @Test
    @Order(5)
    @DisplayName("TC-STORY-20: revoked story token → waiting_for_channel; 0 stories")
    void tcStory20WaitingForChannel() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", "104", "group");
        UUID publicationId = PipelineTestSupport.insertStoryPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                "vk",
                Instant.now().minusSeconds(2),
                "story-vk-20",
                "ttl_24h");

        emulator().post("/_control/vk/story-tokens/104/revoke", "");

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                    .isEqualTo("waiting_for_channel");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT story_connection_status FROM channels WHERE id = ?", String.class, channelId))
                    .isEqualTo("revoked");
        });

        assertStoryCount("vk", 0);
    }

    @Test
    @Order(6)
    @DisplayName("TC-STORY-15/retry: 5xx then success — still one story")
    void tcStoryRetryNoDuplicate() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", "105", "group");
        UUID publicationId = PipelineTestSupport.insertStoryPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                "vk",
                Instant.now().minusSeconds(2),
                "story-vk-retry",
                "ttl_24h");

        emulator()
                .putScenario(
                        """
                        {"networks":{"vk":{"forcedError":"5xx","httpStatus":503}}}
                        """);

        for (int i = 0; i < 2; i++) {
            dispatchService.dispatchDueBatch();
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                    .isEqualTo("failed_retryable"));
            jdbcTemplate.update(
                    "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?",
                    publicationId);
        }

        emulator().putScenario("{\"networks\":{\"vk\":{}}}");
        jdbcTemplate.update(
                "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?", publicationId);
        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        assertStoryCount("vk", 1);
        // erid path sanity: ad with erid also publishes once
        UUID adPub = PipelineTestSupport.insertStoryPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                "vk",
                Instant.now().minusSeconds(1),
                "story-ad-ok",
                "ttl_24h",
                null,
                true,
                ERID);
        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
                        jdbcTemplate.queryForObject("SELECT state FROM publications WHERE id = ?", String.class, adPub))
                .isEqualTo("succeeded"));
        assertStoryCount("vk", 2);
    }

    private void assertStoryCount(String network, long expected) throws Exception {
        JsonNode posts =
                objectMapper.readTree(emulator().get("/_control/posts?network=" + network + "&contentType=story"));
        assertThat(posts.isArray()).isTrue();
        assertThat(posts).hasSize((int) expected);
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/verify"))
                        .path("ok")
                        .asBoolean())
                .isTrue();
    }
}
