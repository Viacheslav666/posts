package ru.posts.messaging.notify;

import java.util.Map;
import java.util.UUID;

/** Channel delivery command for notify.email / notify.tg / notify.push / notify.ws. */
public record NotifyDeliveryCommand(
        UUID deliveryId,
        UUID notificationId,
        UUID userId,
        UUID workspaceId,
        String channel,
        String eventType,
        String dedupeKey,
        Map<String, Object> payload,
        String traceId) {}
