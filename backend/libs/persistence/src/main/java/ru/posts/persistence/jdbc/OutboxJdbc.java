package ru.posts.persistence.jdbc;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** Append-only outbox writes from the publication hot path (WS / notifications / analytics). */
public class OutboxJdbc {

    private final JdbcClient jdbcClient;

    public OutboxJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public void append(String aggregate, UUID aggregateId, String eventType, String payloadJson) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO outbox (aggregate, aggregate_id, event_type, payload)
                        VALUES (:aggregate, :aggregateId, :eventType, CAST(:payload AS jsonb))
                        """)
                .param("aggregate", aggregate)
                .param("aggregateId", aggregateId)
                .param("eventType", eventType)
                .param("payload", payloadJson == null ? "{}" : payloadJson)
                .update();
    }
}
