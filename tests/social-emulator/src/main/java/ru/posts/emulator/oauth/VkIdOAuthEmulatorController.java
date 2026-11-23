package ru.posts.emulator.oauth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * VK ID (id.vk.ru) and publisher {@code /oauth2/auth} stubs for compose profile {@code full}.
 *
 * <p>Mirrors VK ID OAuth 2.1: the {@code authorization_code} grant requires {@code code}, {@code code_verifier},
 * {@code client_id} and {@code device_id}; {@code /oauth2/user_info} wraps the profile in {@code user}.
 */
@RestController
public class VkIdOAuthEmulatorController {

    private static final List<String> AUTH_CODE_REQUIRED = List.of("code", "code_verifier", "client_id", "device_id");

    @GetMapping("/authorize")
    public Map<String, Object> authorize(@RequestParam MultiValueMap<String, String> params) {
        return Map.of("ok", true, "provider", "vk-id", "params", params.toSingleValueMap());
    }

    @PostMapping(
            path = "/oauth2/auth",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(@RequestParam MultiValueMap<String, String> form) {
        if ("authorization_code".equals(form.getFirst("grant_type"))) {
            for (String param : AUTH_CODE_REQUIRED) {
                String value = form.getFirst(param);
                if (value == null || value.isBlank()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "invalid_request", "error_description", param + " is missing"));
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", "emulator-vk-id-access-token");
        body.put("token_type", "bearer");
        body.put("expires_in", 3600);
        body.put("user_id", "10001");
        body.put("email", "vk-id@emulator.local");
        String state = form.getFirst("state");
        if (state != null) {
            body.put("state", state);
        }
        return ResponseEntity.ok(body);
    }

    @RequestMapping(
            value = "/oauth2/user_info",
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> userInfo() {
        return Map.of(
                "user",
                Map.of(
                        "user_id", "10001",
                        "email", "vk-id@emulator.local",
                        "first_name", "Emulator",
                        "last_name", "VkId"));
    }
}
