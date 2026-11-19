package ru.posts.emulator.max;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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
import ru.posts.emulator.store.ModerationStore;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;
import ru.posts.emulator.web.PublishSupport;

@RestController
public class MaxApiController {

    private final PostStore postStore;
    private final ModerationStore moderationStore;
    private final PublishSupport publishSupport;
    private final MaxSubscriptionStore subscriptionStore;
    private final ConcurrentHashMap<String, String> uploadTokens = new ConcurrentHashMap<>();
    private final AtomicLong commentSeq = new AtomicLong();

    public MaxApiController(
            PostStore postStore,
            ModerationStore moderationStore,
            PublishSupport publishSupport,
            MaxSubscriptionStore subscriptionStore) {
        this.postStore = postStore;
        this.moderationStore = moderationStore;
        this.publishSupport = publishSupport;
        this.subscriptionStore = subscriptionStore;
    }

    @PostMapping(
            path = {"/subscriptions", "/max/subscriptions"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscription(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        String token = bearer(authorization);
        String url = body.get("url") == null ? null : String.valueOf(body.get("url"));
        String secret = body.get("secret") == null ? null : String.valueOf(body.get("secret"));
        @SuppressWarnings("unchecked")
        List<String> types =
                body.get("update_types") instanceof List<?> list ? (List<String>) (List<?>) list : List.of();
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
            @RequestHeader(value = "Authorization", required = false) String authorization, @RequestParam String url) {
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

    /** BE-33: DELETE /messages?message_id=… */
    @DeleteMapping(path = {"/max/messages", "/messages"})
    public ResponseEntity<?> deleteMessageByQuery(
            @RequestParam(name = "message_id", required = false) String messageId,
            @RequestParam(required = false) String chat_id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.MAX, rawResponse, () -> {
            if (messageId != null && !messageId.isBlank()) {
                postStore.deleteByRemoteId(Network.MAX, chat_id, messageId);
            }
            return ResponseEntity.ok(Map.of("success", true));
        });
    }

    @DeleteMapping(path = {"/max/messages/{messageId}", "/messages/{messageId}"})
    public ResponseEntity<?> deleteMessage(
            @PathVariable String messageId,
            @RequestParam(required = false) String chat_id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.MAX, rawResponse, () -> {
            postStore.deleteByRemoteId(Network.MAX, chat_id, messageId);
            return ResponseEntity.ok(Map.of("success", true));
        });
    }

    /** BE-33: delete channel comment — not restorable. */
    @DeleteMapping(
            path = {
                "/max/messages/{messageId}/comments",
                "/messages/{messageId}/comments",
                "/max/messages/{messageId}/comments/{commentId}",
                "/messages/{messageId}/comments/{commentId}"
            })
    public ResponseEntity<?> deleteComment(
            @PathVariable String messageId,
            @PathVariable(required = false) String commentId,
            @RequestParam(name = "comment_id", required = false) String commentIdQuery,
            @RequestParam(required = false) String chat_id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.MAX, rawResponse, () -> {
            String id = commentId != null ? commentId : commentIdQuery;
            if (id == null || id.isBlank()) {
                id = "msg:" + messageId;
            }
            String owner = chat_id == null ? messageId : chat_id;
            moderationStore.deleteComment(Network.MAX, owner, id, "channel_comment");
            return ResponseEntity.ok(Map.of("success", true));
        });
    }

    /** BE-33: reply to comment / create comment. */
    @PostMapping(
            path = {"/max/messages/{messageId}/comments", "/messages/{messageId}/comments"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createComment(
            @PathVariable String messageId,
            @RequestParam(required = false) String chat_id,
            @RequestBody(required = false) Map<String, Object> json,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.MAX, rawResponse, () -> {
            String text = json == null ? null : String.valueOf(json.getOrDefault("text", ""));
            if (json != null && json.get("text") == null) {
                text = "";
            }
            String owner = chat_id == null ? messageId : chat_id;
            String commentId = String.valueOf(commentSeq.incrementAndGet());
            ModerationStore.EmulatedComment comment =
                    moderationStore.putComment(Network.MAX, owner, commentId, "channel_comment", text, null, false);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message_id", messageId);
            body.put("comment_id", comment.commentId());
            body.put("text", text == null ? "" : text);
            return ResponseEntity.ok(body);
        });
    }

    /**
     * Restore is not supported on Max — always return an error if somehow called.
     * Path kept for adapter tests that probe capability.
     */
    @PostMapping(
            path = {
                "/max/messages/{messageId}/comments/{commentId}/restore",
                "/messages/{messageId}/comments/{commentId}/restore"
            })
    public ResponseEntity<?> restoreCommentNotSupported(
            @PathVariable String messageId, @PathVariable String commentId, HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.MAX, rawResponse, () -> ResponseEntity.badRequest()
                .body(Map.of("code", "not.supported", "message", "Comment restore is not supported")));
    }

    /** BE-33: remove chat member. */
    @DeleteMapping(path = {"/max/chats/{chatId}/members", "/chats/{chatId}/members"})
    public ResponseEntity<?> deleteChatMember(
            @PathVariable String chatId,
            @RequestParam(name = "user_id", required = false) String userId,
            @RequestBody(required = false) Map<String, Object> json,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.MAX, rawResponse, () -> {
            String uid = userId;
            if ((uid == null || uid.isBlank()) && json != null && json.get("user_id") != null) {
                uid = String.valueOf(json.get("user_id"));
            }
            moderationStore.ban(Network.MAX, chatId, uid, Map.of("removed", true));
            return ResponseEntity.ok(Map.of("success", true));
        });
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
