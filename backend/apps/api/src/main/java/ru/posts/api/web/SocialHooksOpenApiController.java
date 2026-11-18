package ru.posts.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.error.NotImplementedException;

/** OpenAPI-only documentation for social inbound webhooks (runtime impl: SocialHooksController). */
@RestController
@Profile("openapi")
@RequestMapping(path = "/hooks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Social hooks", description = "Inbound webhooks for VK / Telegram / Max (validate + enqueue)")
public class SocialHooksOpenApiController {

    @PostMapping("/vk/{channelId}")
    @Operation(operationId = "hooksVk", summary = "VK Callback API endpoint")
    public ResponseEntity<?> vk(
            @PathVariable UUID channelId,
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Callback-Secret", required = false) String secret) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/tg/{botId}")
    @Operation(operationId = "hooksTg", summary = "Telegram Bot webhook")
    public ResponseEntity<Map<String, Object>> tg(
            @PathVariable UUID botId,
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/max/{botId}")
    @Operation(operationId = "hooksMax", summary = "Max Bot webhook")
    public ResponseEntity<Map<String, Object>> max(
            @PathVariable UUID botId,
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Max-Bot-Api-Secret", required = false) String secret) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/telegram-notify")
    @Operation(
            operationId = "hooksTelegramNotify",
            summary = "Telegram notification-bot webhook (account linking via /start CODE)")
    public ResponseEntity<Map<String, Object>> telegramNotify(
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret) {
        throw new NotImplementedException("openapi");
    }
}
