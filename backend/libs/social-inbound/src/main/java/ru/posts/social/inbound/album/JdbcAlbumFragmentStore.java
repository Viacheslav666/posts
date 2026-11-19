package ru.posts.social.inbound.album;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Postgres album buffer. Every replica inserts fragments; one transaction drains a group only after
 * {@link TelegramAlbumAggregator#WINDOW} of silence, so a partial group is not published as final.
 */
public class JdbcAlbumFragmentStore implements AlbumFragmentStore {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAlbumFragmentStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void add(String channelKey, String mediaGroupId, String botId, JsonNode update, Instant receivedAt) {
        long updateId = update.path("update_id").asLong(update.hashCode());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(update);
        } catch (Exception ex) {
            payload = update.toString();
        }
        jdbc.sql(
                        """
                        INSERT INTO telegram_album_fragments (
                            channel_id, media_group_id, bot_id, update_id, payload, received_at)
                        VALUES (
                            :channelId, :mediaGroupId, :botId, :updateId, CAST(:payload AS jsonb), :receivedAt)
                        ON CONFLICT (channel_id, media_group_id, update_id) DO NOTHING
                        """)
                .param("channelId", UUID.fromString(channelKey))
                .param("mediaGroupId", mediaGroupId)
                .param("botId", UUID.fromString(botId))
                .param("updateId", updateId)
                .param("payload", payload)
                .param("receivedAt", Timestamp.from(receivedAt))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public int pendingCount() {
        Long count = jdbc.sql("SELECT count(*) FROM telegram_album_fragments")
                .query(Long.class)
                .single();
        return count == null ? 0 : Math.toIntExact(count);
    }

    @Override
    @Transactional
    public List<TelegramAlbumAggregator.ReadyAlbum> drainReady(Instant now) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended('tg-album-flush', 0))")
                .query((rs, rowNum) -> 1)
                .list();
        Instant cutoff = now.minus(TelegramAlbumAggregator.WINDOW);
        List<Row> rows = jdbc.sql(
                        """
                        SELECT channel_id, media_group_id, bot_id, update_id, payload::text AS payload
                        FROM telegram_album_fragments
                        WHERE (channel_id, media_group_id) IN (
                            SELECT channel_id, media_group_id
                            FROM telegram_album_fragments
                            GROUP BY channel_id, media_group_id
                            HAVING max(received_at) <= :cutoff
                        )
                        ORDER BY channel_id, media_group_id, update_id
                        FOR UPDATE
                        """)
                .param("cutoff", Timestamp.from(cutoff))
                .query((rs, rowNum) -> new Row(
                        rs.getObject("channel_id", UUID.class),
                        rs.getString("media_group_id"),
                        rs.getObject("bot_id", UUID.class),
                        rs.getString("payload")))
                .list();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, List<Row>> grouped = new LinkedHashMap<>();
        for (Row row : rows) {
            grouped.computeIfAbsent(row.channelId + ":" + row.mediaGroupId, k -> new ArrayList<>())
                    .add(row);
        }
        for (List<Row> group : grouped.values()) {
            Row first = group.getFirst();
            jdbc.sql(
                            """
                            DELETE FROM telegram_album_fragments
                            WHERE channel_id = :channelId AND media_group_id = :mediaGroupId
                            """)
                    .param("channelId", first.channelId)
                    .param("mediaGroupId", first.mediaGroupId)
                    .update();
        }
        List<TelegramAlbumAggregator.ReadyAlbum> ready = new ArrayList<>();
        for (List<Row> group : grouped.values()) {
            Row first = group.getFirst();
            List<JsonNode> fragments = new ArrayList<>();
            for (Row row : group) {
                try {
                    fragments.add(objectMapper.readTree(row.payload));
                } catch (Exception ex) {
                    // skip malformed fragment
                }
            }
            if (!fragments.isEmpty()) {
                ready.add(new TelegramAlbumAggregator.ReadyAlbum(
                        first.channelId.toString(),
                        first.mediaGroupId,
                        first.botId.toString(),
                        List.copyOf(fragments)));
            }
        }
        return ready;
    }

    private record Row(UUID channelId, String mediaGroupId, UUID botId, String payload) {}
}
