package ru.posts.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.error.NotImplementedException;

/** OpenAPI-only documentation for YooKassa webhooks (runtime: {@link YooKassaWebhookController}). */
@RestController
@Profile("openapi")
@RequestMapping(path = "/webhooks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Billing webhooks", description = "YooKassa payment notifications")
public class YooKassaWebhookOpenApiController {

    @PostMapping("/yookassa")
    @Operation(
            operationId = "webhooksYookassa",
            summary = "YooKassa payment webhook",
            description = "IP-allowlisted; idempotent inbox by event_id; verifies payment via YooKassa API.")
    public ResponseEntity<Map<String, Object>> yookassa(@RequestBody JsonNode body) {
        throw new NotImplementedException("openapi");
    }
}
