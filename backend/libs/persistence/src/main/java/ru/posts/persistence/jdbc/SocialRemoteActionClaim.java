package ru.posts.persistence.jdbc;

import java.time.Instant;
import java.util.UUID;

/** Row claimed by publisher for social_remote_actions processing (BE-33). */
public record SocialRemoteActionClaim(
        UUID id,
        Instant runAt,
        UUID workspaceId,
        UUID channelId,
        String network,
        String externalChannelId,
        String kind,
        String targetJson,
        String payloadJson,
        String idempotencyKey,
        String source,
        String sourceRefJson,
        int attempts) {}
