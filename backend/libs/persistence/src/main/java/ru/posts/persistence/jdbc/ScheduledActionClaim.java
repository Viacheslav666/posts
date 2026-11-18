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
        int attempts,
        UUID postId) {

    /** Backward-compatible constructor without postId. */
    public ScheduledActionClaim(
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
            int attempts) {
        this(
                id,
                runAt,
                publicationId,
                publicationRunAt,
                workspaceId,
                channelId,
                network,
                kind,
                remotePostId,
                externalChannelId,
                payloadJson,
                attempts,
                null);
    }
}
