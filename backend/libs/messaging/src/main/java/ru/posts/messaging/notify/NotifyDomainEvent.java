package ru.posts.messaging.notify;

import java.util.Map;
import java.util.UUID;

/** Domain event relayed from outbox to notify.events (BE-11). */
public record NotifyDomainEvent(
        long outboxId,
        String aggregate,
        UUID aggregateId,
        String eventType,
        Map<String, Object> payload,
        String traceId) {}
