package ru.posts.social.api;

import java.util.Map;

/** Successful (or recovered) remote publication reference. */
public record PublishResult(
        String remotePostId,
        String remoteUrl,
        /**
         * Optional assetId → remote media ref produced during publish (e.g. Telegram {@code file_id}).
         * Used by the publisher to populate {@code channel_media_cache} for inline-media networks.
         */
        Map<String, RemoteMediaRef> uploadedMediaByAssetId) {

    public PublishResult(String remotePostId, String remoteUrl) {
        this(remotePostId, remoteUrl, Map.of());
    }

    public PublishResult {
        uploadedMediaByAssetId = uploadedMediaByAssetId == null ? Map.of() : Map.copyOf(uploadedMediaByAssetId);
    }
}
