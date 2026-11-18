package ru.posts.api.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventSubscriptionDtos {

    private EventSubscriptionDtos() {}

    public record EventSubscriptionResponse(
            UUID id,
            UUID channelId,
            UUID workspaceId,
            String network,
            String mode,
            String status,
            List<String> consumers,
            List<String> eventTypes,
            Instant lastEventAt,
            String lastError,
            int failureCount,
            Instant updatedAt,
            /** Present when a custom TG bot webhook was (re)registered — may steal updates from other services. */
            String warning) {}
}
