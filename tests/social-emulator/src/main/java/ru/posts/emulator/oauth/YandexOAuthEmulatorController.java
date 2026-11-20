package ru.posts.emulator.oauth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Yandex OAuth stubs (paths under {@code /yandex} — set {@code POSTS_YANDEX_OAUTH_BASE_URL} accordingly). */
@RestController
@RequestMapping("/yandex")
public class YandexOAuthEmulatorController {

    @GetMapping("/authorize")
    public Map<String, Object> authorize(@RequestParam MultiValueMap<String, String> params) {
        return Map.of("ok", true, "provider", "yandex", "params", params.toSingleValueMap());
    }

    @PostMapping(
            path = "/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(@RequestParam MultiValueMap<String, String> form) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", "emulator-yandex-access-token");
        body.put("token_type", "bearer");
        body.put("expires_in", 3600);
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> userInfo() {
        return Map.of(
                "id", "yandex-10001",
                "login", "emulator_yandex",
                "default_email", "yandex@emulator.local",
                "real_name", "Emulator Yandex");
    }
}