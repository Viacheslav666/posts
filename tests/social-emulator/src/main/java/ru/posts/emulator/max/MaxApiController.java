package ru.posts.emulator.max;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;
import ru.posts.emulator.web.PublishSupport;

@RestController
public class MaxApiController {

    private final PostStore postStore;
    private final PublishSupport publishSupport;

    public MaxApiController(PostStore postStore, PublishSupport publishSupport) {
        this.postStore = postStore;
        this.publishSupport = publishSupport;
    }

    @PostMapping(
            path = {"/max/messages", "/messages"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> sendMessageJson(
            @RequestParam(required = false) String access_token,
            @RequestBody(required = false) Map<String, Object> json,
            HttpServletResponse rawResponse)
            throws IOException {
        return send(access_token, null, json == null ? Map.of() : json, rawResponse);
    }

    @PostMapping(
            path = {"/max/messages", "/messages"},
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> sendMessageForm(
            @RequestParam(required = false) String access_token,
            @RequestParam MultiValueMap<String, String> form,
            HttpServletResponse rawResponse)
            throws IOException {
        return send(access_token, form, Map.of(), rawResponse);
    }

    private ResponseEntity<?> send(
            String accessToken,
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
                || (form != null && form.getFirst("access_token") != null)
                || json.get("access_token") != null;

        return publishSupport.execute(Network.MAX, rawResponse, () -> {
            PostStore.CreateResult result = postStore.createOrGet(
                    Network.MAX,
                    finalChat,
                    idempotencyKey,
                    text,
                    Map.of("method", "messages", "token_present", tokenPresent));

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
