package ru.posts.emulator.billing;

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
    public ResponseEntity<Map<String, Object>> createPayment(@RequestBody(required = false) Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("id", id);
        payment.put("status", "pending");
        payment.put("paid", false);
        payment.put("amount", body == null ? Map.of("value", "1.00", "currency", "RUB") : body.get("amount"));
        return ResponseEntity.ok(payment);
    }

    @GetMapping(path = "/payments/{paymentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getPayment(@PathVariable String paymentId) {
        return Map.of("id", paymentId, "status", "succeeded", "paid", true);
    }
}
