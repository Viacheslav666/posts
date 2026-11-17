package ru.posts.emulator.ok;

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
public class OkApiController {

    private final PostStore postStore;
    private final PublishSupport publishSupport;

    public OkApiController(PostStore postStore, PublishSupport publishSupport) {
        this.postStore = postStore;
        this.publishSupport = publishSupport;
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

    private ResponseEntity<?> dispatch(
            String method,
            MultiValueMap<String, String> form,
            Map<String, Object> json,
            HttpServletResponse rawResponse)
            throws IOException {
        if (method == null || !"mediatopic.post".equals(method)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error_code", 100, "error_msg", "Unsupported method: " + method));
        }
        return mediatopicPost(form, json, rawResponse);
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

        return publishSupport.execute(Network.OK, rawResponse, () -> {
            PostStore.CreateResult result = postStore.createOrGet(
                    Network.OK, finalGid, idempotencyKey, text, Map.of("method", "mediatopic.post"));
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
