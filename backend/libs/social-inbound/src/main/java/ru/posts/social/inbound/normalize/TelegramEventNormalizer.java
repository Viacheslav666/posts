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

/** Normalizes Telegram Update payloads into {@link SocialInboundEvent}. */
public final class TelegramEventNormalizer {

    private TelegramEventNormalizer() {}

    public static SocialInboundEvent normalize(
            JsonNode update, UUID botId, UUID channelId, UUID workspaceId, List<String> consumers, Instant receivedAt) {
        long updateId = update.path("update_id").asLong();
        String dedupKey = botId + ":" + updateId;
        JsonNode message = firstPresent(update, "channel_post", "edited_channel_post", "message", "edited_message");
        SocialEventKind kind = mapKind(update, message);
        Map<String, String> remoteIds = new HashMap<>();
        remoteIds.put("update_id", String.valueOf(updateId));
        remoteIds.put("bot_id", botId.toString());
        if (message != null) {
            remoteIds.put("chat_id", message.path("chat").path("id").asText());
            remoteIds.put("message_id", message.path("message_id").asText());
            if (message.has("media_group_id")) {
                remoteIds.put("media_group_id", message.path("media_group_id").asText());
            }
        }
        SocialInboundAuthor author = authorFrom(message);
        String text = message == null ? null : firstText(message, "text", "caption");
        Instant occurredAt = message != null && message.has("date")
                ? Instant.ofEpochSecond(message.path("date").asLong())
                : receivedAt;
        List<SocialInboundAttachment> attachments = message == null ? List.of() : attachmentsFrom(message);
        return new SocialInboundEvent(
                "tg",
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
                update.toString(),
                consumers);
    }

    /** Merge album fragments into one event with all attachments. */
    public static SocialInboundEvent mergeAlbum(
            List<JsonNode> updates,
            UUID botId,
            UUID channelId,
            UUID workspaceId,
            List<String> consumers,
            Instant receivedAt) {
        if (updates.isEmpty()) {
            throw new IllegalArgumentException("empty album");
        }
        SocialInboundEvent first = normalize(updates.get(0), botId, channelId, workspaceId, consumers, receivedAt);
        List<SocialInboundAttachment> all = new ArrayList<>(first.attachments());
        for (int i = 1; i < updates.size(); i++) {
            SocialInboundEvent next = normalize(updates.get(i), botId, channelId, workspaceId, consumers, receivedAt);
            all.addAll(next.attachments());
        }
        String mediaGroupId = first.remoteIds().get("media_group_id");
        String dedupKey = botId + ":album:" + mediaGroupId;
        Map<String, String> remoteIds = new HashMap<>(first.remoteIds());
        remoteIds.put("album_size", String.valueOf(updates.size()));
        return new SocialInboundEvent(
                first.network(),
                first.channelId(),
                first.workspaceId(),
                SocialEventKind.POST_NEW,
                dedupKey,
                remoteIds,
                first.author(),
                first.text(),
                first.entities(),
                List.copyOf(all),
                first.occurredAt(),
                receivedAt,
                first.raw(),
                consumers);
    }

    private static SocialEventKind mapKind(JsonNode update, JsonNode message) {
        if (update.has("my_chat_member") || update.has("chat_member")) {
            return SocialEventKind.BOT_MEMBERSHIP_CHANGED;
        }
        if (update.has("edited_channel_post") || update.has("edited_message")) {
            return SocialEventKind.POST_EDITED;
        }
        if (message != null && message.path("chat").path("type").asText("").equals("channel")) {
            return SocialEventKind.POST_NEW;
        }
        return SocialEventKind.MESSAGE_NEW;
    }

    private static SocialInboundAuthor authorFrom(JsonNode message) {
        if (message == null) {
            return new SocialInboundAuthor(null, false, false);
        }
        JsonNode from = message.get("from");
        JsonNode senderChat = message.get("sender_chat");
        if (senderChat != null && !senderChat.isNull()) {
            return new SocialInboundAuthor(senderChat.path("id").asText(), true, false);
        }
        if (from == null || from.isNull()) {
            return new SocialInboundAuthor(null, false, false);
        }
        return new SocialInboundAuthor(
                from.path("id").asText(), false, from.path("is_bot").asBoolean(false));
    }

    private static List<SocialInboundAttachment> attachmentsFrom(JsonNode message) {
        List<SocialInboundAttachment> out = new ArrayList<>();
        addPhoto(out, message.path("photo"));
        addMedia(out, "video", message.get("video"));
        addMedia(out, "document", message.get("document"));
        addMedia(out, "audio", message.get("audio"));
        addMedia(out, "animation", message.get("animation"));
        addMedia(out, "voice", message.get("voice"));
        return out;
    }

    private static void addPhoto(List<SocialInboundAttachment> out, JsonNode photoSizes) {
        if (photoSizes == null || !photoSizes.isArray() || photoSizes.isEmpty()) {
            return;
        }
        JsonNode best = photoSizes.get(photoSizes.size() - 1);
        out.add(new SocialInboundAttachment(
                "photo",
                best.path("file_id").asText(),
                null,
                best.path("file_size").isMissingNode()
                        ? null
                        : best.path("file_size").asLong(),
                "image/jpeg"));
    }

    private static void addMedia(List<SocialInboundAttachment> out, String type, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        out.add(new SocialInboundAttachment(
                type,
                node.path("file_id").asText(),
                null,
                node.path("file_size").isMissingNode()
                        ? null
                        : node.path("file_size").asLong(),
                node.path("mime_type").asText(null)));
    }

    private static JsonNode firstPresent(JsonNode update, String... fields) {
        for (String f : fields) {
            JsonNode n = update.get(f);
            if (n != null && !n.isNull() && !n.isMissingNode()) {
                return n;
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.get(f);
            if (n != null && !n.isNull() && !n.asText().isBlank()) {
                return n.asText();
            }
        }
        return null;
    }
}
