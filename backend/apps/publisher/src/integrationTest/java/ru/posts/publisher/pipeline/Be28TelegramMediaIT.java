package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.posts.common.uuid.UuidV7;

/** BE-28 acceptance: Telegram album (10), topic post, 0 duplicates on retry. */
@Tag("ci")
class Be28TelegramMediaIT extends AbstractPipelineIT {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("BE-28: album of 10 media publishes once via sendMediaGroup")
    void albumOfTenMedia() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002810");
        List<UUID> mediaIds = seedReadyImages(workspaceId, 10);

        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        UUID publicationId = insertPublicationWithMedia(
                workspaceId, postId, variantId, channelId, Instant.now().minusSeconds(2), "album-10", mediaIds, "{}");

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            String state = jdbcTemplate.queryForObject(
                    "SELECT state FROM publications WHERE id = ?", String.class, publicationId);
            if (!"succeeded".equals(state)) {
                String err = jdbcTemplate.queryForObject(
                        "SELECT coalesce(error_code,'') || ':' || coalesce(error_message,'') FROM publications WHERE id = ?",
                        String.class,
                        publicationId);
                assertThat(state).as("publication error=%s", err).isEqualTo("succeeded");
            }
        });

        JsonNode posts = objectMapper.readTree(emulator().get("/_control/posts?network=telegram"));
        assertThat(posts.isArray()).isTrue();
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts.get(0).path("metadata").path("method").asText()).isEqualTo("sendMediaGroup");
        assertThat(posts.get(0).path("metadata").path("media_count").asInt()).isEqualTo(10);

        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isEqualTo(1);
        JsonNode verify = objectMapper.readTree(emulator().get("/_control/verify"));
        assertThat(verify.path("ok").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("BE-28: post into Telegram forum topic (message_thread_id)")
    void postToTopic() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002811");
        jdbcTemplate.update("UPDATE channels SET thread_id = ? WHERE id = ?", "42", channelId);

        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                postId,
                variantId,
                channelId,
                Instant.now().minusSeconds(2),
                "topic-hello");

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        JsonNode posts = objectMapper.readTree(emulator().get("/_control/posts?network=telegram"));
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts.get(0).path("metadata").path("message_thread_id").asLong())
                .isEqualTo(42L);
    }

    @Test
    @DisplayName("BE-28: UNKNOWN_OUTCOME / retry does not duplicate album publish")
    void albumRetryNoDuplicates() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002812");
        List<UUID> mediaIds = seedReadyImages(workspaceId, 3);

        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        UUID publicationId = insertPublicationWithMedia(
                workspaceId,
                postId,
                variantId,
                channelId,
                Instant.now().minusSeconds(2),
                "album-retry",
                mediaIds,
                "{\"mute\":true}");

        emulator()
                .putScenario(
                        """
                        {"networks":{"telegram":{"forcedError":"drop"}}}
                        """);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isIn("failed_retryable", "succeeded"));

        emulator().putScenario("{\"networks\":{\"telegram\":{}}}");
        jdbcTemplate.update(
                "UPDATE publications SET next_attempt_at = now() - interval '1 second', state = 'failed_retryable' WHERE id = ? AND state <> 'succeeded'",
                publicationId);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isEqualTo(1);
        JsonNode verify = objectMapper.readTree(emulator().get("/_control/verify"));
        assertThat(verify.path("ok").asBoolean()).isTrue();
    }

    private List<UUID> seedReadyImages(UUID workspaceId, int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UuidV7.generate();
            jdbcTemplate.update(
                    """
                    INSERT INTO media_assets (id, workspace_id, kind, s3_key, mime, size_bytes, sha256, status)
                    VALUES (?, ?, 'image', ?, 'image/jpeg', 4, ?, 'ready')
                    """,
                    id,
                    workspaceId,
                    "it/be28/" + id + ".jpg",
                    "sha-" + id);
            ids.add(id);
        }
        return ids;
    }

    private UUID insertPublicationWithMedia(
            UUID workspaceId,
            UUID postId,
            UUID variantId,
            UUID channelId,
            Instant runAt,
            String text,
            List<UUID> mediaIds,
            String optionsJson)
            throws Exception {
        StringBuilder mediaJson = new StringBuilder("[");
        for (int i = 0; i < mediaIds.size(); i++) {
            if (i > 0) {
                mediaJson.append(',');
            }
            mediaJson.append('"').append(mediaIds.get(i)).append('"');
        }
        mediaJson.append(']');

        jdbcTemplate.update(
                """
                INSERT INTO posts (id, workspace_id, title, base_content, status, content_kind, story_options)
                VALUES (?, ?, 'be28', ?::jsonb, 'scheduled', 'post', '{}'::jsonb)
                """,
                postId,
                workspaceId,
                "{\"text\":\"" + text + "\",\"mediaIds\":" + mediaJson + "}");
        jdbcTemplate.update(
                """
                INSERT INTO post_variants (id, post_id, channel_id, network, content, options)
                VALUES (?, ?, ?, 'tg', ?::jsonb, ?::jsonb)
                """,
                variantId,
                postId,
                channelId,
                "{\"text\":\"" + text + "\",\"mediaIds\":" + mediaJson + "}",
                optionsJson);

        UUID id = UuidV7.generate();
        String key = PipelineTestSupport.sha256(variantId + "|" + runAt + "|be28");
        jdbcTemplate.update(
                """
                INSERT INTO publications (
                    id, workspace_id, post_id, variant_id, channel_id, network,
                    run_at, idempotency_key, state)
                VALUES (?, ?, ?, ?, ?, 'tg', ?, ?, 'pending')
                """,
                id,
                workspaceId,
                postId,
                variantId,
                channelId,
                java.sql.Timestamp.from(runAt),
                key);
        return id;
    }
}
