package ru.posts.social.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Domain event after normalization (BE-32). */
public record SocialInboundEvent(
        String network,
        UUID channelId,
        UUID workspaceId,
        SocialEventKind kind,
        String dedupKey,
        Map<String, String> remoteIds,
        SocialInboundAuthor author,
        String text,
        JsonNode entities,
        List<SocialInboundAttachment> attachments,
        Instant occurredAt,
        Instant receivedAt,
        String raw,
        List<String> consumers) {}
