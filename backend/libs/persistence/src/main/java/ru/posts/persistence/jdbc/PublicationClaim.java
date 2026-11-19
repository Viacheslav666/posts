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
        String source,
        /** ORD erid when creative is registered; null blocks ad publishes (fail-closed). */
        String erid,
        /** Advertiser display name from creative / ad_payload. */
        String advertiserName,
        /** Advertiser INN for the marker line. */
        String advertiserInn,
        /** Auto-delete after ORD display period (days); null if not applicable. */
        Integer adDisplayPeriodDays,
        /** Whether to schedule delete after display period (default true for ads). */
        boolean adAutoDeleteAfterPeriod,
        /** Story TTL enum: ttl_1h|ttl_12h|ttl_24h|ttl_48h. */
        String storyTtl,
        String storyLinkText,
        String storyLinkUrl,
        /** photo | video — derived from media mime / story_options. */
        String mediaKind,
        /**
         * JSON array of media asset UUID strings from variant/base content ({@code mediaIds}).
         * BE-23: publisher resolves these into {@code MediaAssetRef} for upload.
         */
        String mediaIdsJson,
        /** Post timezone for variable rendering (BE-29). */
        String timezone,
        /** Channel title for {@code {group_name}} (BE-29). */
        String channelTitle,
        /**
         * JSON array of VK string attachments from content ({@code stringAttachments}) (BE-29).
         */
        String stringAttachmentsJson,
        /**
         * BE-25 placement mode from ad_payload.placement or legacy markingType (0..5 / enum name).
         * Null → AdMarker defaults to text_start.
         */
        String markingPlacement,
        /** posts.source_ref JSON (BE-36 crosspost lineage). */
        String sourceRefJson) {}
