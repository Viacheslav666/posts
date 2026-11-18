package ru.posts.grabber.fetch;

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
        Map<String, Object> rawPayload) {}
