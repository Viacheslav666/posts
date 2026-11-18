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
import ru.posts.common.uuid.UuidV7;

/**
 * QA-13: advertising VK post with {@code text_end} placement publishes marker at the end (emulator).
 */
@Tag("ci")
class Qa13AdPlacementPipelineIT extends AbstractVkPipelineIT {

    private static final String ERID = "test-erid-qa13-place-0001";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("QA-13: VK ad with text_end placement → marker after body")
    void adTextEndPlacementOnVk() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", "920", "group");
        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        UUID creativeId = UuidV7.generate();
        jdbcTemplate.update(
                """
                INSERT INTO ord_creatives (
                    id, workspace_id, post_id, external_id, erid, provider, status,
                    advertiser_name, advertiser_inn, display_period_days, auto_delete_after_period,
                    registered_at)
                VALUES (?, ?, ?, ?, ?, 'vk_ord', 'registered', 'ООО Рекламодатель', '7707083893', 30, false, now())
                """,
                creativeId,
                workspaceId,
                postId,
                "creative:" + postId + ":qa13",
                ERID);
        String adPayload = "{\"advertiserName\":\"ООО Рекламодатель\",\"inn\":\"7707083893\",\"erid\":\""
                + ERID
                + "\",\"placement\":\"text_end\"}";
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    id, workspace_id, title, base_content, status, content_kind, story_options,
                    is_ad, ad_payload, ord_creative_id)
                VALUES (?, ?, 'qa13-ad-place', '{"text":"Тело рекламного поста"}'::jsonb, 'scheduled', 'post',
                    '{}'::jsonb, true, ?::jsonb, ?)
                """,
                postId,
                workspaceId,
                adPayload,
                creativeId);
        jdbcTemplate.update(
                """
                INSERT INTO post_variants (id, post_id, channel_id, network, content, options)
                VALUES (?, ?, ?, 'vk', '{"text":"Тело рекламного поста"}'::jsonb, '{}'::jsonb)
                """,
                variantId,
                postId,
                channelId);

        UUID publicationId = UuidV7.generate();
        Instant runAt = Instant.now().minusSeconds(2);
        String key = PipelineTestSupport.sha256(variantId + "|" + runAt + "|ad");
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

        JsonNode posts = objectMapper.readTree(emulator().get("/_control/posts?network=vk"));
        assertThat(posts).hasSize(1);
        String text = posts.get(0).path("text").asText();
        assertThat(text).startsWith("Тело рекламного поста");
        assertThat(text).endsWith("erid: " + ERID);
        assertThat(text).contains("Реклама");
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/verify"))
                        .path("ok")
                        .asBoolean())
                .isTrue();
    }
}
