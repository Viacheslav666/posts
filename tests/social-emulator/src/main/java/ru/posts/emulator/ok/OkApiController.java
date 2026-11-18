package ru.posts.emulator.ok;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.posts.emulator.config.EmulatorProperties;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;
import ru.posts.emulator.web.PublishSupport;

@RestController
public class OkApiController {

    private final PostStore postStore;
    private final PublishSupport publishSupport;
    private final EmulatorProperties emulatorProperties;
    private final ConcurrentMap<String, String> momentUploads = new ConcurrentHashMap<>();

    public OkApiController(PostStore postStore, PublishSupport publishSupport, EmulatorProperties emulatorProperties) {
        this.postStore = postStore;
        this.publishSupport = publishSupport;
        this.emulatorProperties = emulatorProperties;
    }

    /** OK form API: {@code POST /ok/fb.do?method=mediatopic.post}. */
    @PostMapping(
            path = {"/ok/fb.do", "/fb.do"},
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> fbDoForm(
            @RequestParam(required = false) String method,
            @RequestParam MultiValueMap<String, String> form,
            HttpServletResponse rawResponse)
            throws IOException {
        return dispatch(method != null ? method : form.getFirst("method"), form, Map.of(), rawResponse);
    }

    /** OK JSON API: {@code POST /ok/fb.do} with JSON body. */
    @PostMapping(
            path = {"/ok/fb.do", "/fb.do"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> fbDoJson(
            @RequestParam(required = false) String method,
            @RequestBody(required = false) Map<String, Object> json,
            HttpServletResponse rawResponse)
            throws IOException {
        Map<String, Object> body = json == null ? Map.of() : json;
        String resolved = method != null ? method : stringValue(body, "method");
        return dispatch(resolved, null, body, rawResponse);
    }

    @PostMapping(
            path = {"/ok/api/mediatopic/post", "/ok/mediatopic/post"},
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> mediatopicPostForm(
            @RequestParam MultiValueMap<String, String> form, HttpServletResponse rawResponse) throws IOException {
        return mediatopicPost(form, Map.of(), rawResponse);
    }

    @PostMapping(
            path = {"/ok/api/mediatopic/post", "/ok/mediatopic/post"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mediatopicPostJson(
            @RequestBody(required = false) Map<String, Object> json, HttpServletResponse rawResponse)
            throws IOException {
        return mediatopicPost(null, json == null ? Map.of() : json, rawResponse);
    }

    @PostMapping(
            path = {"/ok/upload/{uploadId}", "/upload/ok/{uploadId}"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadMoment(
            @PathVariable String uploadId, @RequestPart(name = "file", required = false) MultipartFile file) {
        momentUploads.put(uploadId, file == null ? "empty" : String.valueOf(file.getSize()));
        return Map.of("token", "ok-upload-" + uploadId, "success", true);
    }

    private ResponseEntity<?> dispatch(
            String method,
            MultiValueMap<String, String> form,
            Map<String, Object> json,
            HttpServletResponse rawResponse)
            throws IOException {
        if (method == null) {
            return ResponseEntity.badRequest().body(Map.of("error_code", 100, "error_msg", "Missing method"));
        }
        return switch (method) {
            case "mediatopic.post" -> mediatopicPost(form, json, rawResponse);
            case "mediatopic.delete" -> ResponseEntity.ok(Map.of("success", true));
            case "photosV2.getUploadUrl", "photos.getUploadUrl" -> ResponseEntity.ok(photosUploadUrl(form, json));
            case "stream.get" -> ResponseEntity.ok(streamGet(form, json));
            case "moments.getUploadUrl" -> ResponseEntity.ok(momentsGetUploadUrl(form, json));
            case "moments.commit" -> momentsCommit(form, json, rawResponse);
            case "moments.delete" -> ResponseEntity.ok(Map.of("success", true));
            default -> ResponseEntity.badRequest()
                    .body(Map.of("error_code", 100, "error_msg", "Unsupported method: " + method));
        };
    }

    private Map<String, Object> photosUploadUrl(MultiValueMap<String, String> form, Map<String, Object> json) {
        String uploadId = UUID.randomUUID().toString();
        String uploadUrl = "http://localhost:8090/ok/upload/" + uploadId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("upload_url", uploadUrl);
        body.put("upload_urls", java.util.List.of(uploadUrl));
        body.put("gid", first(form, json, "gid", "group_id"));
        return body;
    }

    private Map<String, Object> streamGet(MultiValueMap<String, String> form, Map<String, Object> json) {
        String gid = first(form, json, "gid", "group_id");
        java.util.List<ru.posts.emulator.store.EmulatedPost> posts =
                postStore.list(Network.OK, gid == null ? null : gid);
        Map<String, Object> topics = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> mediaTopics = new java.util.ArrayList<>();
        for (ru.posts.emulator.store.EmulatedPost post : posts) {
            if (post.metadata() != null && "story".equals(post.metadata().get("contentType"))) {
                continue;
            }
            Map<String, Object> topic = new LinkedHashMap<>();
            topic.put("id", post.remotePostId());
            topic.put("text", post.text());
            topics.put(post.remotePostId(), topic);
            mediaTopics.add(topic);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entities", Map.of("topics", topics));
        body.put("media_topics", mediaTopics);
        return body;
    }

    private Map<String, Object> momentsGetUploadUrl(MultiValueMap<String, String> form, Map<String, Object> json) {
        String uploadId = UUID.randomUUID().toString();
        String uploadUrl = emulatorProperties.absolute("/ok/upload/" + uploadId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("upload_url", uploadUrl);
        body.put("upload_urls", java.util.List.of(uploadUrl));
        body.put("gid", first(form, json, "gid", "group_id"));
        body.put("type", first(form, json, "type"));
        return body;
    }

    private ResponseEntity<?> momentsCommit(
            MultiValueMap<String, String> form, Map<String, Object> json, HttpServletResponse rawResponse)
            throws IOException {
        String gid = first(form, json, "gid", "group_id", "owner_ref");
        if (gid != null && gid.startsWith("group:")) {
            gid = gid.substring("group:".length());
        }
        String text = first(form, json, "text", "attachment", "message");
        String requestedKey = first(form, json, "idempotency_key", "guid");
        String finalGid = gid == null ? "unknown" : gid;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("contentType", "story");
        metadata.put("method", "moments.commit");
        metadata.put("ttl_ms", first(form, json, "ttl_ms"));
        metadata.put("link_url", first(form, json, "link_url"));
        metadata.put("link_text", first(form, json, "link_text"));
        String type = first(form, json, "type");
        metadata.put("mediaKind", type != null && type.toUpperCase().contains("VIDEO") ? "video" : "photo");

        return publishSupport.execute(Network.OK, rawResponse, () -> {
            PostStore.CreateResult result = postStore.createOrGet(Network.OK, finalGid, requestedKey, text, metadata);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("moment_id", result.post().remotePostId());
            body.put("id", result.post().remotePostId());
            body.put("success", true);
            return ResponseEntity.ok(body);
        });
    }

    private ResponseEntity<?> mediatopicPost(
            MultiValueMap<String, String> form, Map<String, Object> json, HttpServletResponse rawResponse)
            throws IOException {
        String gid = first(form, json, "gid", "group_id", "channel_id");
        String text = first(form, json, "attachment", "text", "message");
        String requestedKey = first(form, json, "idempotency_key", "guid");
        String idempotencyKey = requestedKey == null
                ? "ok:" + nullToEmpty(gid) + ":"
                        + Integer.toHexString(nullToEmpty(text).hashCode())
                : requestedKey;

        String finalGid = gid == null ? "unknown" : gid;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("method", "mediatopic.post");
        metadata.put("attachment", text);
        metadata.put("disable_comments", first(form, json, "disable_comments"));
        metadata.put("application_key", first(form, json, "application_key"));
        metadata.put("sig_present", first(form, json, "sig") != null);

        // Prefer plain text from JSON attachment media[0].text when attachment is JSON.
        String plainText = text;
        if (text != null && text.trim().startsWith("{")) {
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
                if (node.path("media").isArray() && !node.path("media").isEmpty()) {
                    plainText = node.path("media").get(0).path("text").asText(text);
                }
            } catch (Exception ignored) {
                plainText = text;
            }
        }
        String finalText = plainText;

        return publishSupport.execute(Network.OK, rawResponse, () -> {
            PostStore.CreateResult result =
                    postStore.createOrGet(Network.OK, finalGid, idempotencyKey, finalText, metadata);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", result.post().remotePostId());
            body.put("success", true);
            return ResponseEntity.ok(body);
        });
    }

    private static String first(MultiValueMap<String, String> form, Map<String, Object> json, String... names) {
        for (String name : names) {
            if (form != null) {
                String value = form.getFirst(name);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            String fromJson = stringValue(json, name);
            if (fromJson != null && !fromJson.isBlank()) {
                return fromJson;
            }
        }
        return null;
    }

    private static String stringValue(Map<String, Object> json, String name) {
        if (json == null || json.get(name) == null) {
            return null;
        }
        return String.valueOf(json.get(name));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
