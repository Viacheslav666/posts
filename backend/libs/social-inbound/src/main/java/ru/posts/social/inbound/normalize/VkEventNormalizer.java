package ru.posts.social.inbound.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundAttachment;
import ru.posts.social.inbound.SocialInboundAuthor;
import ru.posts.social.inbound.SocialInboundEvent;

/** Normalizes VK Callback API payloads into {@link SocialInboundEvent}. */
public final class VkEventNormalizer {

    private VkEventNormalizer() {}

    public static SocialInboundEvent normalize(
            JsonNode body, UUID channelId, UUID workspaceId, List<String> consumers, Instant receivedAt) {
        String type = text(body, "type");
        String eventId = text(body, "event_id");
        if (eventId == null || eventId.isBlank()) {
            eventId = type + ":" + body.path("object").path("id").asText("0") + ":"
                    + body.path("event_id").asText(UUID.randomUUID().toString());
        }
        JsonNode object = body.path("object");
        SocialEventKind kind = mapKind(type, object);
        Map<String, String> remoteIds = new HashMap<>();
        remoteIds.put("event_id", eventId);
        remoteIds.put("group_id", text(body, "group_id"));
        if (object.has("id")) {
            remoteIds.put("post_id", object.path("id").asText());
        }
        if (object.has("post_id")) {
            remoteIds.put("post_id", object.path("post_id").asText());
        }
        SocialInboundAuthor author = authorFrom(object);
        String text = firstText(object, "text", "message");
        Instant occurredAt =
                object.has("date") ? Instant.ofEpochSecond(object.path("date").asLong()) : receivedAt;
        List<SocialInboundAttachment> attachments = attachmentsFrom(object.path("attachments"));
        return new SocialInboundEvent(
                "vk",
                channelId,
                workspaceId,
                kind,
                eventId,
                remoteIds,
                author,
                text,
                object.path("entities").isMissingNode() ? null : object.get("entities"),
                attachments,
                occurredAt,
                receivedAt,
                sanitizeRaw(body),
                consumers);
    }

    private static SocialEventKind mapKind(String type, JsonNode object) {
        if (type == null) {
            return SocialEventKind.POST_NEW;
        }
        return switch (type) {
            case "wall_post_new" -> {
                String postType = object.path("post_type").asText("");
                yield "suggest".equals(postType) ? SocialEventKind.POST_SUGGESTED : SocialEventKind.POST_NEW;
            }
            case "wall_repost" -> SocialEventKind.POST_NEW;
            case "wall_reply_new",
                    "photo_comment_new",
                    "video_comment_new",
                    "board_post_new",
                    "market_comment_new" -> SocialEventKind.COMMENT_NEW;
            case "wall_reply_edit", "photo_comment_edit", "video_comment_edit" -> SocialEventKind.COMMENT_EDITED;
            case "wall_reply_delete",
                    "photo_comment_delete",
                    "video_comment_delete",
                    "board_post_delete" -> SocialEventKind.COMMENT_REMOVED;
            case "message_new" -> SocialEventKind.MESSAGE_NEW;
            case "group_join" -> SocialEventKind.MEMBER_JOINED;
            default -> SocialEventKind.POST_NEW;
        };
    }

    private static SocialInboundAuthor authorFrom(JsonNode object) {
        long fromId = object.path("from_id").asLong(object.path("user_id").asLong(0));
        boolean community = fromId < 0;
        return new SocialInboundAuthor(String.valueOf(fromId), community, false);
    }

    private static List<SocialInboundAttachment> attachmentsFrom(JsonNode attachments) {
        List<SocialInboundAttachment> out = new ArrayList<>();
        if (attachments == null || !attachments.isArray()) {
            return out;
        }
        for (JsonNode a : attachments) {
            String type = a.path("type").asText("unknown");
            JsonNode payload = a.path(type);
            String remoteRef = payload.path("owner_id").asText("") + "_"
                    + payload.path("id").asText("");
            String url =
                    payload.path("sizes").isArray() && payload.path("sizes").size() > 0
                            ? payload.path("sizes")
                                    .get(payload.path("sizes").size() - 1)
                                    .path("url")
                                    .asText(null)
                            : payload.path("url").asText(null);
            out.add(new SocialInboundAttachment(type, remoteRef, url, null, null));
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = text(node, f);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String sanitizeRaw(JsonNode body) {
        // Drop secret from persisted raw
        if (body instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
            com.fasterxml.jackson.databind.node.ObjectNode copy = obj.deepCopy();
            copy.remove("secret");
            return copy.toString();
        }
        return body.toString();
    }
}
