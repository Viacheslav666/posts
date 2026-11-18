package ru.posts.social.inbound.dedup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;
import ru.posts.social.inbound.SocialInboundEvent;

/** JDBC inbox for social_inbound_events with application-level dedup. */
public class InboundEventStore {

    public static final int RAW_MAX_BYTES = 64 * 1024;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public InboundEventStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findExisting(String network, UUID channelId, String dedupKey) {
        return jdbc.sql(
                        """
                        SELECT id FROM social_inbound_events
                        WHERE network = :network AND channel_id = :channelId AND dedup_key = :dedupKey
                        LIMIT 1
                        """)
                .param("network", network)
                .param("channelId", channelId)
                .param("dedupKey", dedupKey)
                .query(UUID.class)
                .optional();
    }

    /**
     * Inserts event if not duplicate. Returns empty when duplicate.
     */
    @Transactional
    public Optional<UUID> insertIfNew(SocialInboundEvent event) {
        Optional<UUID> existing = findExisting(event.network(), event.channelId(), event.dedupKey());
        if (existing.isPresent()) {
            return Optional.empty();
        }
        UUID id = UuidV7.generate();
        Instant receivedAt = event.receivedAt() == null ? Instant.now() : event.receivedAt();
        jdbc.sql(
                        """
                        INSERT INTO social_inbound_events (
                            id, workspace_id, channel_id, network, kind, dedup_key,
                            author_id, author_is_community, author_is_bot, text,
                            remote_ids, entities, attachments, raw, occurred_at, received_at
                        ) VALUES (
                            :id, :workspaceId, :channelId, :network, :kind, :dedupKey,
                            :authorId, :authorIsCommunity, :authorIsBot, :text,
                            CAST(:remoteIds AS jsonb), CAST(:entities AS jsonb), CAST(:attachments AS jsonb),
                            :raw, :occurredAt, :receivedAt
                        )
                        """)
                .param("id", id)
                .param("workspaceId", event.workspaceId())
                .param("channelId", event.channelId())
                .param("network", event.network())
                .param("kind", event.kind().name())
                .param("dedupKey", event.dedupKey())
                .param(
                        "authorId",
                        event.author() == null ? null : event.author().id())
                .param(
                        "authorIsCommunity",
                        event.author() == null ? null : event.author().community())
                .param(
                        "authorIsBot",
                        event.author() == null ? null : event.author().bot())
                .param("text", event.text())
                .param("remoteIds", toJson(event.remoteIds() == null ? java.util.Map.of() : event.remoteIds()))
                .param(
                        "entities",
                        event.entities() == null ? null : event.entities().toString())
                .param("attachments", toJson(event.attachments() == null ? List.of() : event.attachments()))
                .param("raw", truncateRaw(event.raw()))
                .param("occurredAt", ts(event.occurredAt()))
                .param("receivedAt", ts(receivedAt))
                .update();
        return Optional.of(id);
    }

    @Transactional
    public void markProcessed(UUID id, Instant receivedAt) {
        jdbc.sql(
                        """
                        UPDATE social_inbound_events
                        SET processed_at = :processedAt
                        WHERE id = :id AND received_at = :receivedAt
                        """)
                .param("processedAt", ts(Instant.now()))
                .param("id", id)
                .param("receivedAt", ts(receivedAt))
                .update();
    }

    /** JdbcClient binds raw Instant as VARCHAR; PostgreSQL timestamptz needs Timestamp. */
    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public static String truncateRaw(String raw) {
        if (raw == null) {
            return null;
        }
        byte[] bytes = raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= RAW_MAX_BYTES) {
            return raw;
        }
        return new String(bytes, 0, RAW_MAX_BYTES, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
