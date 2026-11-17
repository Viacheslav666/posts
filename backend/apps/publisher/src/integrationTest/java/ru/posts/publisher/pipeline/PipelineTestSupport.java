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
        UUID channelId = UuidV7.generate();
        jdbc.update(
                """
                INSERT INTO channels (id, workspace_id, network, external_id, type, title)
                VALUES (?, ?, 'tg', ?, 'channel', 'Pipeline TG')
                """,
                channelId,
                workspaceId,
                externalId);
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

    static void ensurePost(
            JdbcTemplate jdbc, UUID workspaceId, UUID postId, UUID channelId, UUID variantId, String text) {
        Integer posts = jdbc.queryForObject("SELECT count(*) FROM posts WHERE id = ?", Integer.class, postId);
        if (posts != null && posts > 0) {
            return;
        }
        jdbc.update(
                """
                INSERT INTO posts (id, workspace_id, title, base_content, status)
                VALUES (?, ?, 'pipeline', ?::jsonb, 'scheduled')
                """,
                postId,
                workspaceId,
                "{\"text\":\"" + text.replace("\"", "\\\"") + "\"}");
        jdbc.update(
                """
                INSERT INTO post_variants (id, post_id, channel_id, network, content, options)
                VALUES (?, ?, ?, 'tg', ?::jsonb, '{}'::jsonb)
                """,
                variantId,
                postId,
                channelId,
                "{\"text\":\"" + text.replace("\"", "\\\"") + "\"}");
    }

    static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
