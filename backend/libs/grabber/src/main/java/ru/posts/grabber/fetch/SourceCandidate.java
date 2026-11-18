package ru.posts.grabber.fetch;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalized remote post candidate before filters/dedup. */
public record SourceCandidate(
        String sourceNetwork,
        String sourceId,
        String remotePostId,
        String sourcePermalink,
        String sourceName,
        String contentText,
        String contentHtml,
        List<Map<String, Object>> attachments,
        List<String> mediaHashes,
        List<String> attachmentCanonicalIds,
        boolean markedAsAds,
        boolean isRepost,
        Instant publishedAt,
        boolean signed,
        boolean fromGroup,
        Map<String, Object> rawPayload) {

    public SourceCandidate {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        mediaHashes = mediaHashes == null ? List.of() : List.copyOf(mediaHashes);
        attachmentCanonicalIds = attachmentCanonicalIds == null ? List.of() : List.copyOf(attachmentCanonicalIds);
        // LinkedHashMap: raw payloads may contain null values (Map.copyOf forbids them).
        if (rawPayload == null) {
            rawPayload = Map.of();
        } else {
            Map<String, Object> copy = new LinkedHashMap<>();
            rawPayload.forEach((k, v) -> copy.put(k, v));
            rawPayload = Collections.unmodifiableMap(copy);
        }
    }

    /** Backward-compatible factory used by older call sites/tests. */
    public static SourceCandidate of(
            String sourceNetwork,
            String sourceId,
            String remotePostId,
            String sourcePermalink,
            String sourceName,
            String contentText,
            String contentHtml,
            List<Map<String, Object>> attachments,
            List<String> mediaHashes,
            List<String> attachmentCanonicalIds,
            boolean markedAsAds,
            boolean isRepost,
            Map<String, Object> rawPayload) {
        return new SourceCandidate(
                sourceNetwork,
                sourceId,
                remotePostId,
                sourcePermalink,
                sourceName,
                contentText,
                contentHtml,
                attachments,
                mediaHashes,
                attachmentCanonicalIds,
                markedAsAds,
                isRepost,
                null,
                false,
                true,
                rawPayload);
    }
}
