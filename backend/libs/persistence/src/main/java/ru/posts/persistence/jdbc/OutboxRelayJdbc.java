package ru.posts.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads unpublished outbox rows and marks them after successful broker publish (BE-11).
 *
 * <p>Duplicate publishes under concurrency are expected; consumers dedupe by notification keys.
 */
public class OutboxRelayJdbc {

    private final JdbcClient jdbcClient;

    public OutboxRelayJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public List<OutboxRow> fetchUnpublished(int limit) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, aggregate, aggregate_id, event_type, payload::text AS payload, created_at
                        FROM outbox
                        WHERE published_at IS NULL
                        ORDER BY id
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Transactional
    public boolean markPublished(long id, Instant publishedAt) {
        int updated = jdbcClient
                .sql(
                        """
                        UPDATE outbox SET published_at = :publishedAt
                        WHERE id = :id AND published_at IS NULL
                        """)
                .param("id", id)
                .param("publishedAt", publishedAt)
                .update();
        return updated > 0;
    }

    private static OutboxRow mapRow(ResultSet rs) throws SQLException {
        return new OutboxRow(
                rs.getLong("id"),
                rs.getString("aggregate"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getTimestamp("created_at").toInstant());
    }

    public record OutboxRow(
            long id, String aggregate, UUID aggregateId, String eventType, String payloadJson, Instant createdAt) {}
}
