package ru.posts.persistence.jdbc;

import java.time.Instant;
import java.util.UUID;

/** Row claimed by publisher for scheduled_actions processing. */
public record ScheduledActionClaim(
        UUID id,
        Instant runAt,
        UUID publicationId,
        Instant publicationRunAt,
        UUID workspaceId,
        UUID channelId,
        String network,
        String kind,
        String remotePostId,
        String externalChannelId,
        String payloadJson,
        int attempts) {}
