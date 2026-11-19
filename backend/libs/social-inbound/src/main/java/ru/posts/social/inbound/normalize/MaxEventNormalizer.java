package ru.posts.social.inbound.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundAttachment;
import ru.posts.social.inbound.SocialInboundAuthor;
import ru.posts.social.inbound.SocialInboundEvent;

/** Normalizes Max webhook updates into {@link SocialInboundEvent}. */
public final class MaxEventNormalizer {

    private MaxEventNormalizer() {}

    public static SocialInboundEvent normalize(
            JsonNode body, UUID channelId, UUID workspaceId, List<String> consumers, Instant receivedAt) {
        String updateType = text(body, "update_type");
        if (updateType == null) {
            updateType = text(body, "type");
        }
        String timestamp = body.path("timestamp").asText(body.path("time").asText(""));
        String mid = body.path("message")
                .path("body")
                .path("mid")
                .asText(body.path("message").path("mid").asText(""));
        String commentId =
                body.path("comment").path("id").asText(body.path("comment_id").asText(""));
        String material = updateType + "|" + timestamp + "|" + (mid.isBlank() ? commentId : mid);
        String dedupKey = sha256(material);

        SocialEventKind kind = mapKind(updateType);
        Map<String, String> remoteIds = new HashMap<>();
        remoteIds.put("update_type", updateType == null ? "" : updateType);
        if (!mid.isBlank()) {
            remoteIds.put("mid", mid);
        }
        if (!commentId.isBlank()) {
            remoteIds.put("comment_id", commentId);
        }

        JsonNode message = body.get("message");
        JsonNode comment = body.get("comment");
        String text = null;
        SocialInboundAuthor author = new SocialInboundAuthor(null, false, false);
        List<SocialInboundAttachment> attachments = new ArrayList<>();
        Instant occurredAt = receivedAt;
        if (timestamp != null && !timestamp.isBlank()) {
            try {
                occurredAt = Instant.ofEpochMilli(Long.parseLong(timestamp));
            } catch (NumberFormatException ignored) {
                // keep receivedAt
            }
        }
        if (comment != null && !comment.isNull()) {
            text = comment.path("text").asText(null);
            author = new SocialInboundAuthor(
                    comment.path("sender")
                            .path("user_id")
                            .asText(comment.path("user_id").asText(null)),
                    false,
                    false);
        } else if (message != null && !message.isNull()) {
            text = message.path("body").path("text").asText(message.path("text").asText(null));
            author = new SocialInboundAuthor(
                    message.path("sender").path("user_id").asText(null), false, false);
        }

        return new SocialInboundEvent(
                "max",
                channelId,
                workspaceId,
                kind,
                dedupKey,
                remoteIds,
                author,
                text,
                null,
                attachments,
                occurredAt,
                receivedAt,
                body.toString(),
                consumers);
    }

    private static SocialEventKind mapKind(String updateType) {
        if (updateType == null) {
            return SocialEventKind.MESSAGE_NEW;
        }
        return switch (updateType) {
            case "message_created" -> SocialEventKind.MESSAGE_NEW;
            case "message_removed" -> SocialEventKind.POST_REMOVED;
            case "comment_created" -> SocialEventKind.COMMENT_NEW;
            case "comment_removed" -> SocialEventKind.COMMENT_REMOVED;
            default -> SocialEventKind.MESSAGE_NEW;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
