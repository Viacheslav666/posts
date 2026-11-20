package ru.posts.emulator.billing;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal YooKassa v3 stub ({@code POSTS_YOOKASSA_BASE_URL=http://social-emulator:8090/yookassa/v3}). */
@RestController
@RequestMapping("/yookassa/v3")
public class YooKassaEmulatorController {

    @PostMapping(
            path = "/payments",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
  @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> createPayment(@RequestBody(required = false) Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("id", id);
        payment.put("status", "pending");
        payment.put("paid", false);
        payment.put("amount", body == null ? Map.of("value", "1.00", "currency", "RUB") : body.get("amount"));
        if (body != null) {
            payment.put("metadata", body.get("metadata"));
        }

        String returnUrl = extractReturnUrl(body);
        String workspaceId = metadataValue(body, "workspaceId");
        String paymentId = metadataValue(body, "paymentId");
        String confirmationUrl = buildConfirmationUrl(returnUrl, id, paymentId, workspaceId);

        Map<String, Object> confirmation = new LinkedHashMap<>();
        confirmation.put("type", "redirect");
        confirmation.put("return_url", returnUrl);
        confirmation.put("confirmation_url", confirmationUrl);
        payment.put("confirmation", confirmation);

        return ResponseEntity.ok(payment);
    }

    @GetMapping(path = "/payments/{paymentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getPayment(@PathVariable String paymentId) {
        return Map.of("id", paymentId, "status", "succeeded", "paid", true);
    }

  @SuppressWarnings("unchecked")
    private static String extractReturnUrl(Map<String, Object> body) {
        if (body == null) {
            return "http://localhost:3000/billing/return";
        }
        Object confirmation = body.get("confirmation");
        if (confirmation instanceof Map<?, ?> map) {
            Object url = map.get("return_url");
            if (url instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return "http://localhost:3000/billing/return";
    }

  @SuppressWarnings("unchecked")
    private static String metadataValue(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object metadata = body.get("metadata");
        if (metadata instanceof Map<?, ?> map) {
            Object value = map.get(key);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    private static String buildConfirmationUrl(
            String returnUrl, String providerPaymentId, String paymentId, String workspaceId) {
        String base = returnUrl;
        if (base == null || base.isBlank()) {
            base = "http://localhost:3000/billing/return";
        }
        StringBuilder url = new StringBuilder(base);
        url.append(base.contains("?") ? '&' : '?');
        url.append("mock=emulator");
        url.append("&providerPaymentId=").append(urlEncode(providerPaymentId));
        if (paymentId != null && !paymentId.isBlank()) {
            url.append("&paymentId=").append(urlEncode(paymentId));
        }
        if (workspaceId != null && !workspaceId.isBlank()) {
            url.append("&workspaceId=").append(urlEncode(workspaceId));
        }
        return url.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}