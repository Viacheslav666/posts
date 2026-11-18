package ru.posts.emulator.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;
import ru.posts.emulator.web.PublishSupport;

@RestController
public class TelegramApiController {

    private final PostStore postStore;
    private final PublishSupport publishSupport;

    public TelegramApiController(PostStore postStore, PublishSupport publishSupport) {
        this.postStore = postStore;
        this.publishSupport = publishSupport;
    }

    @PostMapping(
            path = {"/bot{token}/sendMessage", "/telegram/bot{token}/sendMessage"},
            consumes = {
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.ALL_VALUE
            })
    public ResponseEntity<?> sendMessage(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form,
            HttpServletResponse rawResponse)
            throws IOException {
        String chatId = stringParam(jsonBody, form, "chat_id");
        String text = stringParam(jsonBody, form, "text");
        String idempotencyKey = stringParam(jsonBody, form, "idempotency_key");
        if (idempotencyKey == null) {
            idempotencyKey = contentKey(chatId, text, null);
        }
        return publish(token, chatId, text, idempotencyKey, Map.of("method", "sendMessage"), rawResponse);
    }

    @PostMapping(
            path = {"/bot{token}/sendPhoto", "/telegram/bot{token}/sendPhoto"},
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<?> sendPhoto(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form,
            @RequestParam(required = false) MultipartFile photo,
            HttpServletResponse rawResponse)
            throws IOException {
        String chatId = stringParam(jsonBody, form, "chat_id");
        String caption = stringParam(jsonBody, form, "caption");
        String photoRef = photo != null ? photo.getOriginalFilename() : stringParam(jsonBody, form, "photo");
        String idempotencyKey = stringParam(jsonBody, form, "idempotency_key");
        if (idempotencyKey == null) {
            idempotencyKey = contentKey(chatId, caption, photoRef);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("method", "sendPhoto");
        meta.put("photo", photoRef);
        return publish(token, chatId, caption, idempotencyKey, meta, rawResponse);
    }

    @PostMapping(
            path = {"/bot{token}/sendMediaGroup", "/telegram/bot{token}/sendMediaGroup"},
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<?> sendMediaGroup(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form,
            HttpServletResponse rawResponse)
            throws IOException {
        String chatId = stringParam(jsonBody, form, "chat_id");
        String media = stringParam(jsonBody, form, "media");
        String requestedKey = stringParam(jsonBody, form, "idempotency_key");
        String idempotencyKey = requestedKey == null ? contentKey(chatId, media, "media_group") : requestedKey;
        String channelId = chatId == null ? "unknown" : chatId;
        return publishSupport.execute(Network.TELEGRAM, rawResponse, () -> {
            PostStore.CreateResult result = postStore.createOrGet(
                    Network.TELEGRAM,
                    channelId,
                    idempotencyKey,
                    media,
                    Map.of("method", "sendMediaGroup", "bot", maskToken(token)));

            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = baseMessage(chatId, result.post().remotePostId());
            message.put("media_group_id", UUID.randomUUID().toString().substring(0, 8));
            messages.add(message);
            return ResponseEntity.ok(Map.of("ok", true, "result", messages));
        });
    }

    @PostMapping(
            path = {"/bot{token}/deleteMessage", "/telegram/bot{token}/deleteMessage"},
            consumes = {
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.ALL_VALUE
            })
    public ResponseEntity<?> deleteMessage(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form) {
        String chatId = stringParam(jsonBody, form, "chat_id");
        String messageId = stringParam(jsonBody, form, "message_id");
        postStore.deleteByRemoteId(Network.TELEGRAM, chatId, messageId);
        // Idempotent: deleting an already-deleted message is success.
        return ResponseEntity.ok(Map.of("ok", true, "result", true));
    }

    @PostMapping(
            path = {
                "/bot{token}/pinChatMessage",
                "/telegram/bot{token}/pinChatMessage",
                "/bot{token}/unpinChatMessage",
                "/telegram/bot{token}/unpinChatMessage"
            },
            consumes = {
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.ALL_VALUE
            })
    public ResponseEntity<?> pinOrUnpin(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form,
            jakarta.servlet.http.HttpServletRequest request) {
        String chatId = stringParam(jsonBody, form, "chat_id");
        String messageId = stringParam(jsonBody, form, "message_id");
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        boolean pinned = !uri.contains("unpin");
        postStore.markPinned(Network.TELEGRAM, chatId, messageId, pinned);
        return ResponseEntity.ok(Map.of("ok", true, "result", true));
    }

    private ResponseEntity<?> publish(
            String token,
            String chatId,
            String text,
            String idempotencyKey,
            Map<String, Object> metadata,
            HttpServletResponse rawResponse)
            throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>(metadata);
        meta.put("bot", maskToken(token));
        return publishSupport.execute(Network.TELEGRAM, rawResponse, () -> {
            PostStore.CreateResult result = postStore.createOrGet(
                    Network.TELEGRAM, chatId == null ? "unknown" : chatId, idempotencyKey, text, meta);
            Map<String, Object> message = baseMessage(chatId, result.post().remotePostId());
            if (text != null) {
                message.put("text", text);
                message.put("caption", text);
            }
            return ResponseEntity.ok(Map.of("ok", true, "result", message));
        });
    }

    private static Map<String, Object> baseMessage(String chatId, String messageId) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("message_id", Long.parseLong(messageId));
        message.put("date", System.currentTimeMillis() / 1000);
        message.put("chat", Map.of("id", parseChatId(chatId), "type", "channel"));
        return message;
    }

    private static Object parseChatId(String chatId) {
        if (chatId == null) {
            return 0;
        }
        try {
            return Long.parseLong(chatId);
        } catch (NumberFormatException ex) {
            return chatId;
        }
    }

    private String stringParam(Map<String, Object> json, MultiValueMap<String, String> form, String name) {
        if (json != null && json.get(name) != null) {
            Object value = json.get(name);
            if (value instanceof JsonNode node) {
                return node.isNull() ? null : node.asText();
            }
            return String.valueOf(value);
        }
        if (form != null) {
            return form.getFirst(name);
        }
        return null;
    }

    private static String contentKey(String chatId, String text, String extra) {
        return "tg:" + nullToEmpty(chatId) + ":"
                + Integer.toHexString((nullToEmpty(text) + "|" + nullToEmpty(extra)).hashCode());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "…" + token.substring(token.length() - 4);
    }
}
