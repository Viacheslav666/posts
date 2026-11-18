package ru.posts.emulator.max;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;
import ru.posts.emulator.web.PublishSupport;

@RestController
public class MaxApiController {

    private final PostStore postStore;
    private final PublishSupport publishSupport;
    private final MaxSubscriptionStore subscriptionStore;
    private final ConcurrentHashMap<String, String> uploadTokens = new ConcurrentHashMap<>();

    public MaxApiController(
            PostStore postStore, PublishSupport publishSupport, MaxSubscriptionStore subscriptionStore) {
        this.postStore = postStore;
        this.publishSupport = publishSupport;
        this.subscriptionStore = subscriptionStore;
    }

    @PostMapping(path = {"/subscriptions", "/max/subscriptions"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscription(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        String token = bearer(authorization);
        String url = body.get("url") == null ? null : String.valueOf(body.get("url"));
        String secret = body.get("secret") == null ? null : String.valueOf(body.get("secret"));
        @SuppressWarnings("unchecked")
        List<String> types = body.get("update_types") instanceof List<?> list
                ? (List<String>) (List<?>) list
                : List.of();
        subscriptionStore.put(token, url, secret, types);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @org.springframework.web.bind.annotation.GetMapping(path = {"/subscriptions", "/max/subscriptions"})
    public ResponseEntity<?> listSubscriptions(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = bearer(authorization);
        List<Map<String, Object>> subscriptions = new java.util.ArrayList<>();
        for (MaxSubscriptionStore.Subscription s : subscriptionStore.list(token)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("url", s.url());
            item.put("update_types", s.updateTypes());
            subscriptions.add(item);
        }
        return ResponseEntity.ok(Map.of("subscriptions", subscriptions));
    }

    @DeleteMapping(path = {"/subscriptions", "/max/subscriptions"})
    public ResponseEntity<?> deleteSubscription(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String url) {
        subscriptionStore.delete(bearer(authorization), url);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private static String bearer(String authorization) {
        if (authorization == null) {
            return "";
        }
        String v = authorization.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return v.substring(7).trim();
        }
        return v;
    }

    @PostMapping(
            path = {"/max/uploads", "/uploads"},
            consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> initUpload(
            @RequestParam String type, @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = "max-up-" + UUID.randomUUID();
        uploadTokens.put(token, type == null ? "image" : type);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", "/max/uploads/" + token);
        body.put("token", token);
        return ResponseEntity.ok(body);
    }

    @PostMapping(
            path = {"/max/uploads/{token}", "/uploads/{token}"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> completeUpload(
            @PathVariable String token, @RequestParam(required = false) MultipartFile data) {
        String type = uploadTokens.getOrDefault(token, "image");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("type", type);
        if (data != null) {
            body.put("bytes", data.getSize());
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping(
            path = {"/max/messages", "/messages"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> sendMessageJson(
            @RequestParam(required = false) String access_token,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, Object> json,
            HttpServletResponse rawResponse)
            throws IOException {
        return send(access_token, authorization, null, json == null ? Map.of() : json, rawResponse);
    }

    @PostMapping(
            path = {"/max/messages", "/messages"},
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> sendMessageForm(
            @RequestParam(required = false) String access_token,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam MultiValueMap<String, String> form,
            HttpServletResponse rawResponse)
            throws IOException {
        return send(access_token, authorization, form, Map.of(), rawResponse);
    }

    @DeleteMapping(path = {"/max/messages/{messageId}", "/messages/{messageId}"})
    public ResponseEntity<?> deleteMessage(
            @PathVariable String messageId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // Emulator treats delete as success even when the message is already gone.
        return ResponseEntity.ok(Map.of("success", true));
    }

    private ResponseEntity<?> send(
            String accessToken,
            String authorization,
            MultiValueMap<String, String> form,
            Map<String, Object> json,
            HttpServletResponse rawResponse)
            throws IOException {
        String chatId = first(form, json, "chat_id", "channel_id", "user_id");
        String text = extractText(json, form);
        String requestedKey = first(form, json, "idempotency_key", "guid");
        String idempotencyKey = requestedKey == null
                ? "max:" + nullToEmpty(chatId) + ":"
                        + Integer.toHexString(nullToEmpty(text).hashCode())
                : requestedKey;

        String finalChat = chatId == null ? "unknown" : chatId;
        boolean tokenPresent = accessToken != null
                || (authorization != null && !authorization.isBlank())
                || (form != null && form.getFirst("access_token") != null)
                || json.get("access_token") != null;

        int attachmentCount = 0;
        if (json.get("attachments") instanceof List<?> list) {
            attachmentCount = list.size();
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("method", "messages");
        meta.put("token_present", tokenPresent);
        meta.put("attachment_count", attachmentCount);
        if (json.get("notify") != null) {
            meta.put("notify", json.get("notify"));
        }

        return publishSupport.execute(Network.MAX, rawResponse, () -> {
            PostStore.CreateResult result = postStore.createOrGet(Network.MAX, finalChat, idempotencyKey, text, meta);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put(
                    "message",
                    Map.of(
                            "body",
                            Map.of("mid", result.post().remotePostId(), "text", text == null ? "" : text),
                            "recipient",
                            Map.of("chat_id", finalChat)));
            return ResponseEntity.ok(body);
        });
    }

    private static String extractText(Map<String, Object> json, MultiValueMap<String, String> form) {
        String direct = first(form, json, "text", "message");
        if (direct != null) {
            return direct;
        }
        if (json.get("body") instanceof Map<?, ?> body) {
            Object text = body.get("text");
            if (text != null) {
                return String.valueOf(text);
            }
        }
        return null;
    }

    private static String first(MultiValueMap<String, String> form, Map<String, Object> json, String... names) {
        for (String name : names) {
            if (form != null) {
                String value = form.getFirst(name);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            if (json.get(name) != null) {
                return String.valueOf(json.get(name));
            }
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
