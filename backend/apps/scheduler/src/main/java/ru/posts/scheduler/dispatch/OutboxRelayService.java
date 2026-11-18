package ru.posts.scheduler.dispatch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import ru.posts.messaging.notify.NotifyDomainEvent;
import ru.posts.messaging.topology.NotifyTopology;
import ru.posts.messaging.trace.AmqpTraceHeaders;
import ru.posts.persistence.jdbc.OutboxRelayJdbc;
import ru.posts.scheduler.config.SchedulerProperties;

@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final OutboxRelayJdbc outboxRelayJdbc;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final SchedulerProperties properties;

    public OutboxRelayService(
            OutboxRelayJdbc outboxRelayJdbc,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            SchedulerProperties properties) {
        this.outboxRelayJdbc = outboxRelayJdbc;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** Publishes unpublished outbox rows to {@code notify} exchange; leaves failed rows unpublished. */
    public int relayBatch() {
        int limit = Math.max(1, properties.outboxRelayBatchSize());
        List<OutboxRelayJdbc.OutboxRow> rows = outboxRelayJdbc.fetchUnpublished(limit);
        if (rows.isEmpty()) {
            return 0;
        }
        int published = 0;
        long timeoutMs = properties.confirmTimeout().toMillis();
        for (OutboxRelayJdbc.OutboxRow row : rows) {
            try {
                Map<String, Object> payload = parsePayload(row.payloadJson());
                String trace = AmqpTraceHeaders.currentOrNew(null);
                NotifyDomainEvent event = new NotifyDomainEvent(
                        row.id(), row.aggregate(), row.aggregateId(), row.eventType(), payload, trace);
                CorrelationData correlation = new CorrelationData("outbox-" + row.id());
                rabbitTemplate.convertAndSend(
                        NotifyTopology.NOTIFY_EXCHANGE,
                        row.eventType(),
                        event,
                        AmqpTraceHeaders.withTraceparent(trace),
                        correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
                if (confirm == null || !confirm.isAck()) {
                    log.warn("Outbox {} confirm nack/timeout for eventType={}", row.id(), row.eventType());
                    continue;
                }
                if (outboxRelayJdbc.markPublished(row.id(), Instant.now())) {
                    published++;
                }
            } catch (Exception ex) {
                log.warn("Outbox relay failed for id={} eventType={}: {}", row.id(), row.eventType(), ex.toString());
            }
        }
        return published;
    }

    private Map<String, Object> parsePayload(String payloadJson) throws Exception {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(payloadJson, MAP_TYPE);
    }
}
