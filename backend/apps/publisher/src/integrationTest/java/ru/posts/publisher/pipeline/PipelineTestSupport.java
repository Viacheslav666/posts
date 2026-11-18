package ru.posts.publisher.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.posts.common.uuid.UuidV7;

final class PipelineTestSupport {

    private PipelineTestSupport() {}

    static UUID seedWorkspace(JdbcTemplate jdbc) {
        UUID userId = UuidV7.generate();
        UUID workspaceId = UuidV7.generate();
        UUID planId = jdbc.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);
        jdbc.update(
                "INSERT INTO users (id, email, name) VALUES (?, ?, ?)",
                userId,
                "pipeline-" + userId + "@example.com",
                "Pipeline");
        jdbc.update(
                """
                INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status)
                VALUES (?, ?, ?, ?, 'trial')
                """,
                workspaceId,
                "Pipeline WS",
                userId,
                planId);
        return workspaceId;
    }

    static UUID seedChannel(JdbcTemplate jdbc, UUID workspaceId, String externalId) {
        return seedChannel(jdbc, workspaceId, "tg", externalId, "channel");
    }

    static UUID seedChannel(JdbcTemplate jdbc, UUID workspaceId, String network, String externalId, String type) {
        UUID channelId = UuidV7.generate();
        jdbc.update(
                """
                INSERT INTO channels (id, workspace_id, network, external_id, type, title, story_connection_status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                channelId,
                workspaceId,
                network,
                externalId,
                type,
                "Pipeline " + network,
                "vk".equals(network) ? "ready" : "missing");
        return channelId;
    }

    static UUID insertPublication(
            JdbcTemplate jdbc,
            UUID workspaceId,
            UUID postId,
            UUID variantId,
            UUID channelId,
            Instant runAt,
            String text)
            throws Exception {
        ensurePost(jdbc, workspaceId, postId, channelId, variantId, text);
        UUID id = UuidV7.generate();
        String key = sha256(variantId + "|" + runAt);
        jdbc.update(
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
                Timestamp.from(runAt),
                key);
        return id;
    }

    /**
     * Seeds an advertising post (+ optional registered ORD creative) and a pending Telegram
     * publication for TC-AD publisher regression.
     */
    static UUID insertAdPublication(
            JdbcTemplate jdbc,
            UUID workspaceId,
            UUID postId,
            UUID variantId,
            UUID channelId,
            Instant runAt,
            String text,
            String erid,
            String advertiserName,
            String advertiserInn,
            int displayPeriodDays,
            boolean autoDeleteAfterPeriod)
            throws Exception {
        ensureAdPost(
                jdbc,
                workspaceId,
                postId,
                channelId,
                variantId,
                text,
                erid,
                advertiserName,
                advertiserInn,
                displayPeriodDays,
                autoDeleteAfterPeriod);
        UUID id = UuidV7.generate();
        String key = sha256(variantId + "|" + runAt + "|ad");
        jdbc.update(
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
                Timestamp.from(runAt),
                key);
        return id;
    }

    static void ensureAdPost(
            JdbcTemplate jdbc,
            UUID workspaceId,
            UUID postId,
            UUID channelId,
            UUID variantId,
            String text,
            String erid,
            String advertiserName,
            String advertiserInn,
            int displayPeriodDays,
            boolean autoDeleteAfterPeriod) {
        Integer posts = jdbc.queryForObject("SELECT count(*) FROM posts WHERE id = ?", Integer.class, postId);
        if (posts != null && posts > 0) {
            return;
        }
        UUID creativeId = null;
        if (erid != null && !erid.isBlank()) {
            creativeId = UuidV7.generate();
            jdbc.update(
                    """
                    INSERT INTO ord_creatives (
                        id, workspace_id, post_id, external_id, erid, provider, status,
                        advertiser_name, advertiser_inn, display_period_days, auto_delete_after_period,
                        registered_at)
                    VALUES (?, ?, ?, ?, ?, 'vk_ord', 'registered', ?, ?, ?, ?, now())
                    """,
                    creativeId,
                    workspaceId,
                    postId,
                    "creative:" + postId + ":0",
                    erid,
                    advertiserName,
                    advertiserInn,
                    displayPeriodDays,
                    autoDeleteAfterPeriod);
        }
        String adPayload = "{\"advertiserName\":\""
                + escapeJson(advertiserName)
                + "\",\"inn\":\""
                + escapeJson(advertiserInn)
                + "\""
                + (erid == null || erid.isBlank() ? "" : ",\"erid\":\"" + escapeJson(erid) + "\"")
                + "}";
        jdbc.update(
                """
                INSERT INTO posts (
                    id, workspace_id, title, base_content, status, content_kind, story_options,
                    is_ad, ad_payload, ord_creative_id)
                VALUES (?, ?, 'ad-pipeline', ?::jsonb, 'scheduled', 'post', '{}'::jsonb, true, ?::jsonb, ?)
                """,
                postId,
                workspaceId,
                "{\"text\":\"" + escapeJson(text) + "\"}",
                adPayload,
                creativeId);
        String network = jdbc.queryForObject("SELECT network FROM channels WHERE id = ?", String.class, channelId);
        if (network == null) {
            network = "tg";
        }
        jdbc.update(
                """
                INSERT INTO post_variants (id, post_id, channel_id, network, content, options)
                VALUES (?, ?, ?, ?, ?::jsonb, '{}'::jsonb)
                """,
                variantId,
                postId,
                channelId,
                network,
                "{\"text\":\"" + escapeJson(text) + "\"}");
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static void ensurePost(
            JdbcTemplate jdbc, UUID workspaceId, UUID postId, UUID channelId, UUID variantId, String text) {
        ensurePost(jdbc, workspaceId, postId, channelId, variantId, text, "post", null);
    }

    static void ensurePost(
            JdbcTemplate jdbc,
            UUID workspaceId,
            UUID postId,
            UUID channelId,
            UUID variantId,
            String text,
            String contentKind,
            String storyTtl) {
        Integer posts = jdbc.queryForObject("SELECT count(*) FROM posts WHERE id = ?", Integer.class, postId);
        if (posts != null && posts > 0) {
            return;
        }
        jdbc.update(
                """
                INSERT INTO posts (id, workspace_id, title, base_content, status, content_kind, story_ttl, story_options)
                VALUES (?, ?, 'pipeline', ?::jsonb, 'scheduled', ?, ?, '{}'::jsonb)
                """,
                postId,
                workspaceId,
                "{\"text\":\"" + text.replace("\"", "\\\"") + "\"}",
                contentKind == null ? "post" : contentKind,
                storyTtl);
        String network = jdbc.queryForObject("SELECT network FROM channels WHERE id = ?", String.class, channelId);
        if (network == null) {
            network = "tg";
        }
        jdbc.update(
                """
                INSERT INTO post_variants (id, post_id, channel_id, network, content, options)
                VALUES (?, ?, ?, ?, ?::jsonb, '{}'::jsonb)
                """,
                variantId,
                postId,
                channelId,
                network,
                "{\"text\":\"" + text.replace("\"", "\\\"") + "\"}");
    }

    static UUID insertStoryPublication(
            JdbcTemplate jdbc,
            UUID workspaceId,
            UUID postId,
            UUID variantId,
            UUID channelId,
            String network,
            Instant runAt,
            String text,
            String storyTtl)
            throws Exception {
        return insertStoryPublication(
                jdbc, workspaceId, postId, variantId, channelId, network, runAt, text, storyTtl, null, false, null);
    }

    static UUID insertStoryPublication(
            JdbcTemplate jdbc,
            UUID workspaceId,
            UUID postId,
            UUID variantId,
            UUID channelId,
            String network,
            Instant runAt,
            String text,
            String storyTtl,
            Instant deleteAfter,
            boolean ad,
            String erid)
            throws Exception {
        ensureStoryPost(jdbc, workspaceId, postId, channelId, variantId, text, storyTtl, deleteAfter, ad, erid);
        UUID id = UuidV7.generate();
        String key = sha256(variantId + "|" + runAt + "|story");
        jdbc.update(
                """
                INSERT INTO publications (
                    id, workspace_id, post_id, variant_id, channel_id, network,
                    run_at, idempotency_key, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending')
                """,
                id,
                workspaceId,
                postId,
                variantId,
                channelId,
                network,
                Timestamp.from(runAt),
                key);
        return id;
    }

    static void ensureStoryPost(
            JdbcTemplate jdbc,
            UUID workspaceId,
            UUID postId,
            UUID channelId,
            UUID variantId,
            String text,
            String storyTtl,
            Instant deleteAfter,
            boolean ad,
            String erid) {
        Integer posts = jdbc.queryForObject("SELECT count(*) FROM posts WHERE id = ?", Integer.class, postId);
        if (posts != null && posts > 0) {
            return;
        }
        UUID creativeId = null;
        if (ad && erid != null && !erid.isBlank()) {
            creativeId = UuidV7.generate();
            jdbc.update(
                    """
                    INSERT INTO ord_creatives (
                        id, workspace_id, post_id, external_id, erid, provider, status,
                        advertiser_name, advertiser_inn, display_period_days, auto_delete_after_period,
                        registered_at)
                    VALUES (?, ?, ?, ?, ?, 'vk_ord', 'registered', 'ООО Тест', '7707083893', 30, false, now())
                    """,
                    creativeId,
                    workspaceId,
                    postId,
                    "creative:" + postId + ":story",
                    erid);
        }
        String adPayload = ad
                ? "{\"advertiserName\":\"ООО Тест\",\"inn\":\"7707083893\""
                        + (erid == null || erid.isBlank() ? "" : ",\"erid\":\"" + escapeJson(erid) + "\"")
                        + "}"
                : null;
        jdbc.update(
                """
                INSERT INTO posts (
                    id, workspace_id, title, base_content, status, content_kind, story_ttl, story_options,
                    is_ad, ad_payload, ord_creative_id)
                VALUES (?, ?, 'story-pipeline', ?::jsonb, 'scheduled', 'story', ?, '{}'::jsonb, ?, ?::jsonb, ?)
                """,
                postId,
                workspaceId,
                "{\"text\":\"" + escapeJson(text) + "\"}",
                storyTtl,
                ad,
                adPayload,
                creativeId);
        String network = jdbc.queryForObject("SELECT network FROM channels WHERE id = ?", String.class, channelId);
        if (network == null) {
            network = "vk";
        }
        String optionsJson = deleteAfter == null ? "{}" : "{\"deleteAfter\":\"" + deleteAfter.toString() + "\"}";
        jdbc.update(
                """
                INSERT INTO post_variants (id, post_id, channel_id, network, content, options)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """,
                variantId,
                postId,
                channelId,
                network,
                "{\"text\":\"" + escapeJson(text) + "\",\"mediaKind\":\"photo\"}",
                optionsJson);
    }

    static void setStoryConnectionStatus(JdbcTemplate jdbc, UUID channelId, String status) {
        jdbc.update(
                "UPDATE channels SET story_connection_status = ?, updated_at = now() WHERE id = ?", status, channelId);
    }

    static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
