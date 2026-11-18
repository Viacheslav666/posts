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
        int attempts,
        /** post | story — BE-16 extension point. */
        String contentKind,
        /** Raw post_variants.options JSON for scheduled_actions materialization. */
        String optionsJson,
        /** posts.ad — BE-15 ORD fail-closed / marker insertion hook. */
        boolean ad,
        /** posts.source — BE-17 grabber attribution (manual|grabber|rss|…). */
        String source) {}
