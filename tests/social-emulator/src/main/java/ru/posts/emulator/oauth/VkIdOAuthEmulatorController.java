package ru.posts.emulator.oauth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * VK ID (id.vk.ru) and publisher {@code /oauth2/auth} stubs for compose profile {@code full}.
 */
@RestController
public class VkIdOAuthEmulatorController {

    @GetMapping("/authorize")
    public Map<String, Object> authorize(@RequestParam MultiValueMap<String, String> params) {
        return Map.of("ok", true, "provider", "vk-id", "params", params.toSingleValueMap());
    }

    @PostMapping(
            path = "/oauth2/auth",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(@RequestParam MultiValueMap<String, String> form) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", "emulator-vk-id-access-token");
        body.put("token_type", "bearer");
        body.put("expires_in", 3600);
        body.put("user_id", "10001");
        body.put("email", "vk-id@emulator.local");
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/oauth2/user_info", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> userInfo() {
        return Map.of(
                "user_id", "10001",
                "id", "10001",
                "email", "vk-id@emulator.local",
                "first_name", "Emulator",
                "last_name", "VkId");
    }
}
