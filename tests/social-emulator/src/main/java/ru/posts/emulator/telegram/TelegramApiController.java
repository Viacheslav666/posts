package ru.posts.emulator.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ru.posts.emulator.store.EmulatedPost;
import ru.posts.emulator.store.ModerationStore;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;
import ru.posts.emulator.web.PublishSupport;

@RestController
public class TelegramApiController {

    static final Duration DELETE_WINDOW = Duration.ofHours(48);

    private final PostStore postStore;
    private final ModerationStore moderationStore;
    private final PublishSupport publishSupport;
    private final ObjectMapper objectMapper;
    private final TelegramWebhookStore webhookStore;
    /** chat_id → linked discussion group id for getChat (BE-33). */
    private final ConcurrentHashMap<String, Long> linkedChats = new ConcurrentHashMap<>();

    public TelegramApiController(
            PostStore postStore,
            ModerationStore moderationStore,
            PublishSupport publishSupport,
            ObjectMapper objectMapper,
            TelegramWebhookStore webhookStore) {
        this.postStore = postStore;
        this.moderationStore = moderationStore;
        this.publishSupport = publishSupport;
        this.objectMapper = objectMapper;
        this.webhookStore = webhookStore;
        // Default: channel -1001 has discussion group -1002
        linkedChats.put("-1001", -1002L);
    }

    @PostMapping(
            path = {"/bot{token}/setWebhook", "/telegram/bot{token}/setWebhook"},
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE})
    public Map<String, Object> setWebhook(@PathVariable String token, @RequestBody Map<String, Object> body) {
        String url = body.get("url") == null ? null : String.valueOf(body.get("url"));
        String secret = body.get("secret_token") == null ? null : String.valueOf(body.get("secret_token"));
        @SuppressWarnings("unchecked")
        List<String> allowed =
                body.get("allowed_updates") instanceof List<?> list ? (List<String>) (List<?>) list : List.of();
        webhookStore.set(token, url, secret, allowed);
        return Map.of("ok", true, "result", true, "description", "Webhook was set");
    }

    @PostMapping(path = {"/bot{token}/getWebhookInfo", "/telegram/bot{token}/getWebhookInfo"})
    public Map<String, Object> getWebhookInfoPost(@PathVariable String token) {
        return getWebhookInfo(token);
    }

    @org.springframework.web.bind.annotation.GetMapping(
            path = {"/bot{token}/getWebhookInfo", "/telegram/bot{token}/getWebhookInfo"})
    public Map<String, Object> getWebhookInfo(@PathVariable String token) {
        TelegramWebhookStore.Webhook wh = webhookStore.get(token);
        Map<String, Object> result = new LinkedHashMap<>();
        if (wh == null) {
            result.put("url", "");
            result.put("has_custom_certificate", false);
            result.put("pending_update_count", 0);
        } else {
            result.put("url", wh.url());
            result.put("has_custom_certificate", false);
            result.put("pending_update_count", wh.pendingUpdateCount());
            if (wh.lastErrorMessage() != null) {
                result.put("last_error_message", wh.lastErrorMessage());
            }
            result.put("allowed_updates", wh.allowedUpdates());
        }
        return Map.of("ok", true, "result", result);
    }

    @PostMapping(
            path = {"/bot{token}/deleteWebhook", "/telegram/bot{token}/deleteWebhook"},
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE})
    public Map<String, Object> deleteWebhook(@PathVariable String token) {
        webhookStore.delete(token);
        return Map.of("ok", true, "result", true, "description", "Webhook was deleted");
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
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("method", "sendMessage");
        putCommonMeta(meta, jsonBody, form, token);
        return publish(token, chatId, text, idempotencyKey, meta, rawResponse, null);
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
        return sendSingleMedia(token, jsonBody, form, photo, "sendPhoto", "photo", rawResponse);
    }

    @PostMapping(
            path = {"/bot{token}/sendVideo", "/telegram/bot{token}/sendVideo"},
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<?> sendVideo(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form,
            @RequestParam(required = false) MultipartFile video,
            HttpServletResponse rawResponse)
            throws IOException {
        return sendSingleMedia(token, jsonBody, form, video, "sendVideo", "video", rawResponse);
    }

    @PostMapping(
            path = {"/bot{token}/sendDocument", "/telegram/bot{token}/sendDocument"},
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<?> sendDocument(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form,
            @RequestParam(required = false) MultipartFile document,
            HttpServletResponse rawResponse)
            throws IOException {
        return sendSingleMedia(token, jsonBody, form, document, "sendDocument", "document", rawResponse);
    }

    @PostMapping(
            path = {"/bot{token}/sendMediaGroup", "/telegram/bot{token}/sendMediaGroup"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> sendMediaGroupJson(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            HttpServletResponse rawResponse)
            throws IOException {
        return sendMediaGroupInternal(token, jsonBody, null, null, rawResponse);
    }

    @PostMapping(
            path = {"/bot{token}/sendMediaGroup", "/telegram/bot{token}/sendMediaGroup"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> sendMediaGroupMultipart(
            @PathVariable String token,
            @RequestParam MultiValueMap<String, String> form,
            MultipartHttpServletRequest multipartRequest,
            HttpServletResponse rawResponse)
            throws IOException {
        return sendMediaGroupInternal(token, null, form, multipartRequest, rawResponse);
    }

    private ResponseEntity<?> sendMediaGroupInternal(
            String token,
            Map<String, Object> jsonBody,
            MultiValueMap<String, String> form,
            MultipartHttpServletRequest multipartRequest,
            HttpServletResponse rawResponse)
            throws IOException {
        String chatId = stringParam(jsonBody, form, "chat_id");
        String media = stringParam(jsonBody, form, "media");
        String requestedKey = stringParam(jsonBody, form, "idempotency_key");
        String idempotencyKey = requestedKey == null ? contentKey(chatId, media, "media_group") : requestedKey;
        String channelId = chatId == null ? "unknown" : chatId;

        int mediaCount = countMediaItems(media);
        int uploadParts =
                multipartRequest == null ? 0 : multipartRequest.getFileMap().size();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("method", "sendMediaGroup");
        meta.put("media_count", mediaCount);
        meta.put("upload_parts", uploadParts);
        putCommonMeta(meta, jsonBody, form, token);

        return publishSupport.execute(Network.TELEGRAM, rawResponse, () -> {
            PostStore.CreateResult result =
                    postStore.createOrGet(Network.TELEGRAM, channelId, idempotencyKey, media, meta);

            List<Map<String, Object>> messages = new ArrayList<>();
            String groupId = UUID.randomUUID().toString().substring(0, 8);
            int count = Math.max(1, mediaCount);
            for (int i = 0; i < count; i++) {
                Map<String, Object> message = baseMessage(chatId, result.post().remotePostId());
                if (i > 0) {
                    // Distinct synthetic ids for album members while keeping store row unique by idempotency.
                    message.put("message_id", Long.parseLong(result.post().remotePostId()) + i);
                }
                message.put("media_group_id", groupId);
                message.put(
                        "photo", List.of(Map.of("file_id", "file:" + groupId + ":" + i, "width", 100, "height", 100)));
                String thread = stringParam(jsonBody, form, "message_thread_id");
                if (thread != null) {
                    message.put("message_thread_id", parseLongOrString(thread));
                }
                messages.add(message);
            }
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
            @RequestParam MultiValueMap<String, String> form,
            HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.TELEGRAM, rawResponse, () -> {
            String chatId = stringParam(jsonBody, form, "chat_id");
            String messageId = stringParam(jsonBody, form, "message_id");
            ResponseEntity<?> tooOld = rejectIfOlderThanDeleteWindow(chatId, messageId);
            if (tooOld != null) {
                return tooOld;
            }
            postStore.deleteByRemoteId(Network.TELEGRAM, chatId, messageId);
            return ResponseEntity.ok(Map.of("ok", true, "result", true));
        });
    }

    @PostMapping(
            path = {"/bot{token}/deleteMessages", "/telegram/bot{token}/deleteMessages"},
            consumes = {
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.ALL_VALUE
            })
    public ResponseEntity<?> deleteMessages(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form,
            HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.TELEGRAM, rawResponse, () -> {
            String chatId = stringParam(jsonBody, form, "chat_id");
            List<String> messageIds = messageIdsParam(jsonBody, form);
            for (String messageId : messageIds) {
                ResponseEntity<?> tooOld = rejectIfOlderThanDeleteWindow(chatId, messageId);
                if (tooOld != null) {
                    return tooOld;
                }
            }
            for (String messageId : messageIds) {
                postStore.deleteByRemoteId(Network.TELEGRAM, chatId, messageId);
            }
            return ResponseEntity.ok(Map.of("ok", true, "result", true));
        });
    }

    @PostMapping(
            path = {"/bot{token}/banChatMember", "/telegram/bot{token}/banChatMember"},
            consumes = {
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.ALL_VALUE
            })
    public ResponseEntity<?> banChatMember(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form,
            HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.TELEGRAM, rawResponse, () -> {
            String chatId = stringParam(jsonBody, form, "chat_id");
            String userId = stringParam(jsonBody, form, "user_id");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("until_date", stringParam(jsonBody, form, "until_date"));
            payload.put("revoke_messages", stringParam(jsonBody, form, "revoke_messages"));
            moderationStore.ban(Network.TELEGRAM, chatId, userId, payload);
            return ResponseEntity.ok(Map.of("ok", true, "result", true));
        });
    }

    @PostMapping(
            path = {"/bot{token}/unbanChatMember", "/telegram/bot{token}/unbanChatMember"},
            consumes = {
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.ALL_VALUE
            })
    public ResponseEntity<?> unbanChatMember(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form,
            HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.TELEGRAM, rawResponse, () -> {
            String chatId = stringParam(jsonBody, form, "chat_id");
            String userId = stringParam(jsonBody, form, "user_id");
            moderationStore.unban(Network.TELEGRAM, chatId, userId);
            return ResponseEntity.ok(Map.of("ok", true, "result", true));
        });
    }

    @PostMapping(
            path = {"/bot{token}/restrictChatMember", "/telegram/bot{token}/restrictChatMember"},
            consumes = {
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.ALL_VALUE
            })
    public ResponseEntity<?> restrictChatMember(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form,
            HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.TELEGRAM, rawResponse, () -> {
            String chatId = stringParam(jsonBody, form, "chat_id");
            String userId = stringParam(jsonBody, form, "user_id");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("until_date", stringParam(jsonBody, form, "until_date"));
            payload.put("permissions", jsonBody != null ? jsonBody.get("permissions") : null);
            payload.put("restricted", true);
            moderationStore.ban(Network.TELEGRAM, chatId, "restrict:" + nullToEmpty(userId), payload);
            return ResponseEntity.ok(Map.of("ok", true, "result", true));
        });
    }

    @PostMapping(
            path = {"/bot{token}/getChat", "/telegram/bot{token}/getChat"},
            consumes = {
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.ALL_VALUE
            })
    public ResponseEntity<?> getChat(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, Object> jsonBody,
            @RequestParam MultiValueMap<String, String> form) {
        String chatId = stringParam(jsonBody, form, "chat_id");
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("id", parseChatId(chatId));
        chat.put("type", "channel");
        chat.put("title", "Emulated chat " + nullToEmpty(chatId));
        Long linked = linkedChats.get(chatId == null ? "" : chatId);
        if (linked == null && chatId != null) {
            // Deterministic linked group for any channel id ending with digits.
            linked = linkedChats.computeIfAbsent(chatId, id -> {
                try {
                    long n = Long.parseLong(id);
                    return n > 0 ? -n - 1 : n - 1;
                } catch (NumberFormatException ex) {
                    return -1002L;
                }
            });
        }
        if (linked != null) {
            chat.put("linked_chat_id", linked);
        }
        return ResponseEntity.ok(Map.of("ok", true, "result", chat));
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

    private ResponseEntity<?> rejectIfOlderThanDeleteWindow(String chatId, String messageId) {
        Optional<EmulatedPost> found = postStore.findByRemoteId(Network.TELEGRAM, chatId, messageId);
        if (found.isEmpty()) {
            // Unknown message: treat as deletable (idempotent success path for missing).
            return null;
        }
        Instant createdAt = found.get().createdAt();
        if (createdAt != null && createdAt.isBefore(Instant.now().minus(DELETE_WINDOW))) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "ok", false, "error_code", 400, "description", "Bad Request: message can't be deleted"));
        }
        return null;
    }

    private List<String> messageIdsParam(Map<String, Object> jsonBody, MultiValueMap<String, String> form) {
        List<String> ids = new ArrayList<>();
        if (jsonBody != null && jsonBody.get("message_ids") instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    ids.add(String.valueOf(item));
                }
            }
        }
        String raw = stringParam(jsonBody, form, "message_ids");
        if (ids.isEmpty() && raw != null) {
            String cleaned = raw.replace("[", "").replace("]", "");
            for (String part : cleaned.split(",")) {
                String trimmed = part.trim().replace("\"", "");
                if (!trimmed.isEmpty()) {
                    ids.add(trimmed);
                }
            }
        }
        return ids;
    }

    private ResponseEntity<?> sendSingleMedia(
            String token,
            Map<String, Object> jsonBody,
            MultiValueMap<String, String> form,
            MultipartFile file,
            String method,
            String field,
            HttpServletResponse rawResponse)
            throws IOException {
        String chatId = stringParam(jsonBody, form, "chat_id");
        String caption = stringParam(jsonBody, form, "caption");
        String mediaRef = file != null ? file.getOriginalFilename() : stringParam(jsonBody, form, field);
        String idempotencyKey = stringParam(jsonBody, form, "idempotency_key");
        if (idempotencyKey == null) {
            idempotencyKey = contentKey(chatId, caption, mediaRef);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("method", method);
        meta.put(field, mediaRef);
        if (file != null) {
            meta.put("bytes", file.getSize());
            meta.put("multipart", true);
        }
        putCommonMeta(meta, jsonBody, form, token);

        Map<String, Object> mediaPayload = new LinkedHashMap<>();
        if ("photo".equals(field)) {
            mediaPayload.put(
                    "photo", List.of(Map.of("file_id", "file:" + nullToEmpty(mediaRef), "width", 100, "height", 100)));
        } else if ("video".equals(field)) {
            mediaPayload.put("video", Map.of("file_id", "file:" + nullToEmpty(mediaRef), "duration", 1));
        } else {
            mediaPayload.put(
                    "document", Map.of("file_id", "file:" + nullToEmpty(mediaRef), "file_name", nullToEmpty(mediaRef)));
        }
        return publish(token, chatId, caption, idempotencyKey, meta, rawResponse, mediaPayload);
    }

    private ResponseEntity<?> publish(
            String token,
            String chatId,
            String text,
            String idempotencyKey,
            Map<String, Object> metadata,
            HttpServletResponse rawResponse,
            Map<String, Object> messageExtras)
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
            if (messageExtras != null) {
                message.putAll(messageExtras);
            }
            Object thread = meta.get("message_thread_id");
            if (thread != null) {
                message.put("message_thread_id", thread);
            }
            return ResponseEntity.ok(Map.of("ok", true, "result", message));
        });
    }

    private void putCommonMeta(
            Map<String, Object> meta, Map<String, Object> jsonBody, MultiValueMap<String, String> form, String token) {
        meta.put("bot", maskToken(token));
        String thread = stringParam(jsonBody, form, "message_thread_id");
        if (thread != null) {
            meta.put("message_thread_id", parseLongOrString(thread));
        }
        String disable = stringParam(jsonBody, form, "disable_notification");
        if (disable != null) {
            meta.put("disable_notification", Boolean.parseBoolean(disable));
        }
        String replyMarkup = stringParam(jsonBody, form, "reply_markup");
        if (replyMarkup != null) {
            meta.put("reply_markup", replyMarkup);
        } else if (jsonBody != null && jsonBody.get("reply_markup") != null) {
            meta.put("reply_markup", jsonBody.get("reply_markup"));
        }
    }

    private int countMediaItems(String mediaJson) {
        if (mediaJson == null || mediaJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(mediaJson);
            return node.isArray() ? node.size() : 1;
        } catch (Exception ex) {
            return 1;
        }
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

    private static Object parseLongOrString(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return value;
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
