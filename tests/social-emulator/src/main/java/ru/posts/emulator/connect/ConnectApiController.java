package ru.posts.emulator.connect;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class ConnectApiController {

    private final ConnectFixtureStore store;

    public ConnectApiController(ConnectFixtureStore store) {
        this.store = store;
    }

    @GetMapping({"/oauth/vk/authorize", "/oauth/ok/authorize"})
    public ResponseEntity<String> authorize(
            HttpServletRequest request, @RequestParam MultiValueMap<String, String> params) {
        String redirectUri = first(params, "redirect_uri");
        String state = first(params, "state");
        if (redirectUri == null || redirectUri.isBlank()) {
            return ResponseEntity.badRequest().body("missing redirect_uri");
        }
        if (!isAllowedChannelRedirect(redirectUri)) {
            return ResponseEntity.badRequest().body("redirect_uri not allowed");
        }

        String defaultCode = request.getRequestURI().contains("/oauth/ok/") ? "ok-admin" : "vk-admin";
        String code = first(params, "fixture_code");
        if (code == null || code.isBlank()) {
            code = defaultCode;
        }

        URI target = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .queryParam("state", state == null ? "" : state)
                .build(true)
                .toUri();

        return ResponseEntity.status(302).location(target).build();
    }

    @PostMapping(
            path = {"/oauth/vk/token", "/oauth/ok/token"},
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> token(@RequestParam MultiValueMap<String, String> form) {
        String code = first(form, "code");
        Map<String, Object> fixture = store.consumeOAuthCode(code);
        if (fixture == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid_grant", "error_description", "unknown code"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", fixture.get("access_token"));
        body.put("refresh_token", fixture.getOrDefault("refresh_token", ""));
        body.put("expires_in", 3600);
        body.put("user_id", fixture.get("user_id"));
        body.put("name", fixture.get("name"));
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/method/groups.get", "/vk/method/groups.get"})
    public Map<String, Object> groupsGet(@RequestParam MultiValueMap<String, String> params) {
        String token = first(params, "access_token");
        Map<String, Object> fixture = findByAccessToken(token);
        List<?> groups = fixture == null ? List.of() : (List<?>) fixture.getOrDefault("groups", List.of());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", groups.size());
        response.put("items", groups);
        return Map.of("response", response);
    }

    @GetMapping({"/method/groups.getById", "/vk/method/groups.getById"})
    public Map<String, Object> groupsGetById(@RequestParam MultiValueMap<String, String> params) {
        String token = first(params, "access_token");
        Map<String, Object> fixture = findByAccessToken(token);
        if (fixture == null) {
            return Map.of("error", Map.of("error_code", 5, "error_msg", "User authorization failed"));
        }
        return Map.of("response", fixture.getOrDefault("groups", List.of()));
    }

    @GetMapping({"/ok/fb.do", "/fb.do"})
    public Object okMethod(@RequestParam MultiValueMap<String, String> params) {
        String method = first(params, "method");
        String token = first(params, "access_token");
        Map<String, Object> fixture = findByAccessToken(token);
        if ("group.getUserGroupsByIds".equals(method) || "group.getInfo".equals(method)) {
            if (fixture == null) {
                return Map.of("error_code", 102, "error_msg", "PARAM_SESSION_EXPIRED");
            }
            List<?> groups = (List<?>) fixture.getOrDefault("groups", List.of());
            if ("group.getInfo".equals(method)) {
                return groups;
            }
            return Map.of("groups", groups);
        }
        return Map.of("error_code", 100, "error_msg", "Unsupported method: " + method);
    }

    @GetMapping({"/bot{token}/getChat", "/telegram/bot{token}/getChat"})
    public ResponseEntity<?> getChat(@PathVariable String token, @RequestParam("chat_id") String chatId) {
        ConnectFixtureStore.ChatFixture chat = store.findTelegram(chatId);
        if (chat == null || !chat.botIsAdmin()) {
            return ResponseEntity.ok(
                    Map.of("ok", false, "error_code", 400, "description", "Bad Request: chat not found"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", Long.parseLong(chat.id()));
        result.put("type", chat.type());
        result.put("title", chat.title());
        result.put("username", chat.username());
        return ResponseEntity.ok(Map.of("ok", true, "result", result));
    }

    @GetMapping({"/bot{token}/getChatMember", "/telegram/bot{token}/getChatMember"})
    public ResponseEntity<?> getChatMember(@PathVariable String token, @RequestParam("chat_id") String chatId) {
        ConnectFixtureStore.ChatFixture chat = store.findTelegram(chatId);
        if (chat == null || !chat.botIsAdmin()) {
            return ResponseEntity.ok(
                    Map.of("ok", false, "error_code", 400, "description", "Bad Request: chat not found"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "administrator");
        result.put("can_post_messages", chat.canPost());
        result.put("can_edit_messages", chat.canPost());
        result.put("can_delete_messages", chat.canPost());
        result.put("can_pin_messages", chat.canPost());
        return ResponseEntity.ok(Map.of("ok", true, "result", result));
    }

    @GetMapping({"/max/chats/{chatId}", "/chats/{chatId}"})
    public ResponseEntity<?> maxChat(@PathVariable String chatId, @RequestParam(required = false) String access_token) {
        ConnectFixtureStore.ChatFixture chat = store.findMax(chatId);
        if (chat == null) {
            return ResponseEntity.status(404).body(Map.of("error", "not_found"));
        }
        if (!chat.canPost()) {
            return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chat.id());
        body.put("title", chat.title());
        body.put("type", chat.type());
        body.put("username", chat.username());
        body.put("can_write", true);
        body.put("permissions", Map.of("write", true));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/_control/connect/reset")
    public Map<String, Object> resetConnect() {
        store.seed();
        return Map.of("ok", true);
    }

    private Map<String, Object> findByAccessToken(String token) {
        if (token == null) {
            return null;
        }
        for (String code : List.of("vk-admin", "vk-no-admin", "ok-admin", "ok-no-admin")) {
            Map<String, Object> fixture = store.consumeOAuthCode(code);
            if (fixture != null && token.equals(fixture.get("access_token"))) {
                return fixture;
            }
        }
        return null;
    }

    private static String first(MultiValueMap<String, String> params, String key) {
        List<String> values = params.get(key);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    /** Emulator-only: reject open redirects; allow Posts channel OAuth callback paths only. */
    static boolean isAllowedChannelRedirect(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return false;
            }
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return false;
            }
            return path.matches(".*/app/[^/]+/channels/connect/(vk|ok)/callback/?");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
