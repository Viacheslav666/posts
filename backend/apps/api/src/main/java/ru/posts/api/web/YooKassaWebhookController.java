package ru.posts.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.billing.BillingWebhookService;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.yookassa.YooKassaIpValidator;

@RestController
@Profile("!openapi")
@RequestMapping(path = "/webhooks", produces = MediaType.APPLICATION_JSON_VALUE)
@Hidden
public class YooKassaWebhookController {

    private final BillingWebhookService webhookService;
    private final YooKassaIpValidator ipValidator;

    public YooKassaWebhookController(BillingWebhookService webhookService, YooKassaIpValidator ipValidator) {
        this.webhookService = webhookService;
        this.ipValidator = ipValidator;
    }

    @PostMapping("/yookassa")
    public ResponseEntity<Map<String, Object>> yookassa(@RequestBody JsonNode body, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        if (!ipValidator.isAllowed(clientIp)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ProblemCodes.FORBIDDEN, "Webhook source IP not allowed");
        }
        webhookService.ingest(body);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
