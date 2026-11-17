package ru.posts.persistence.jdbc;

import java.time.Instant;
import java.util.UUID;

/** Row claimed by publisher for in_progress processing. */
public record PublicationClaim(
        UUID id,
        Instant runAt,
        UUID workspaceId,
        UUID postId,
        UUID variantId,
        UUID channelId,
        String network,
        String idempotencyKey,
        String externalChannelId,
        String contentText,
        String remotePostId,
        int attempts) {}
