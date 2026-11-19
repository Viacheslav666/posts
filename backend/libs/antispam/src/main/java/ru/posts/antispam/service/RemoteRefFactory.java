package ru.posts.antispam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import ru.posts.antispam.model.AntispamSection;
import ru.posts.social.inbound.SocialInboundEvent;

/** Builds remote_ref / comment keys / moderation targets from inbound events. */
public class RemoteRefFactory {

    private final ObjectMapper objectMapper;

    public RemoteRefFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record RemoteIds(
            String commentType,
            String ownerId,
            String remotePostId,
            String remoteCommentId,
            String remoteUserId,
            String remoteCommentKey,
            Map<String, Object> remoteRef) {}

    public RemoteIds from(SocialInboundEvent event, AntispamSection section) {
        Map<String, String> ids = event.remoteIds() == null ? Map.of() : event.remoteIds();
        String network = event.network() == null ? "" : event.network().toLowerCase(Locale.ROOT);
        JsonNode raw = readRaw(event.raw());

        return switch (network) {
            case "vk" -> fromVk(event, section, ids, raw);
            case "tg", "telegram" -> fromTg(event, ids, raw);
            case "max" -> fromMax(event, section, ids, raw);
            default -> new RemoteIds(
                    "wall",
                    null,
                    ids.get("post_id"),
                    ids.get("comment_id"),
                    event.author() == null ? null : event.author().id(),
                    fallbackKey(event),
                    Map.of());
        };
    }

    private RemoteIds fromVk(
            SocialInboundEvent event, AntispamSection section, Map<String, String> ids, JsonNode raw) {
        JsonNode object = raw.path("object");
        String ownerId = firstNonBlank(
                ids.get("owner_id"),
                text(object, "owner_id"),
                negativeGroup(ids.get("group_id")));
        String commentId = firstNonBlank(ids.get("comment_id"), text(object, "id"));
        String postId = firstNonBlank(ids.get("post_id"), text(object, "post_id"), text(object, "id"));
        if (section == AntispamSection.WALL_COMMENT
                || section == AntispamSection.PHOTO_COMMENT
                || section == AntispamSection.VIDEO_COMMENT
                || section == AntispamSection.MARKET_COMMENT
                || section == AntispamSection.BOARD_COMMENT) {
            // for comments post_id is separate; comment id is object.id
            commentId = firstNonBlank(ids.get("comment_id"), text(object, "id"));
            postId = firstNonBlank(ids.get("post_id"), text(object, "post_id"), text(object, "photo_id"), text(object, "video_id"), text(object, "item_id"), text(object, "topic_id"));
        }
        String commentType =
                switch (section) {
                    case PHOTO_COMMENT -> "photo";
                    case VIDEO_COMMENT -> "video";
                    case MARKET_COMMENT -> "market";
                    case BOARD_COMMENT -> "board";
                    case WALL_POST, SUGGESTED_POST -> "wall";
                    default -> "wall";
                };
        String key = section.wire() + ":" + nullToEmpty(ownerId) + ":" + nullToEmpty(postId) + ":"
                + nullToEmpty(commentId);
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("owner_id", ownerId);
        ref.put("post_id", postId);
        ref.put("comment_id", commentId);
        ref.put("comment_type", commentType);
        return new RemoteIds(
                commentType,
                ownerId,
                postId,
                commentId,
                event.author() == null ? null : event.author().id(),
                key,
                ref);
    }

    private RemoteIds fromTg(SocialInboundEvent event, Map<String, String> ids, JsonNode raw) {
        String chatId = firstNonBlank(ids.get("chat_id"), text(raw.path("message").path("chat"), "id"), text(raw.path("edited_message").path("chat"), "id"));
        String messageId = firstNonBlank(ids.get("message_id"), text(raw.path("message"), "message_id"), text(raw.path("edited_message"), "message_id"));
        String key = "tg:" + nullToEmpty(chatId) + ":" + nullToEmpty(messageId);
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("chat_id", chatId);
        ref.put("message_id", messageId);
        ref.put("comment_type", "channel_comment");
        return new RemoteIds(
                "channel_comment",
                chatId,
                messageId,
                messageId,
                event.author() == null ? null : event.author().id(),
                key,
                ref);
    }

    private RemoteIds fromMax(
            SocialInboundEvent event, AntispamSection section, Map<String, String> ids, JsonNode raw) {
        String mid = firstNonBlank(ids.get("mid"), ids.get("message_id"));
        String commentId = firstNonBlank(ids.get("comment_id"), text(raw.path("comment"), "id"));
        String key = "max:" + nullToEmpty(mid) + ":" + nullToEmpty(commentId);
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("mid", mid);
        ref.put("comment_id", commentId);
        ref.put("comment_type", "channel_comment");
        ref.put("section", section == null ? null : section.wire());
        return new RemoteIds(
                "channel_comment",
                null,
                mid,
                commentId == null ? mid : commentId,
                event.author() == null ? null : event.author().id(),
                key,
                ref);
    }

    private JsonNode readRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            return objectMapper.nullNode();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText(null);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String negativeGroup(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return null;
        }
        if (groupId.startsWith("-")) {
            return groupId;
        }
        return "-" + groupId;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String fallbackKey(SocialInboundEvent event) {
        return event.dedupKey() == null ? "unknown" : event.dedupKey();
    }
}
