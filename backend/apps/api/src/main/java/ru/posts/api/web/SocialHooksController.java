package ru.posts.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.channel.IngestPublisher;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.persistence.entity.TelegramBotEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.TelegramBotRepository;

/**
 * Social inbound webhooks: validate secret quickly and publish raw payload to ingest.
 * Full processing lives in posts-ingest.
 */
@RestController
@Profile("!openapi")
@RequestMapping(path = "/hooks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Social hooks", description = "Inbound webhooks for VK / Telegram / Max (validate + enqueue)")
public class SocialHooksController {

    private final ChannelRepository channelRepository;
    private final TelegramBotRepository telegramBotRepository;
    private final IngestPublisher ingestPublisher;
    private final ConcurrentHashMap<UUID, String> vkSecrets = new ConcurrentHashMap<>();

    public SocialHooksController(
            ChannelRepository channelRepository,
            TelegramBotRepository telegramBotRepository,
            IngestPublisher ingestPublisher) {
        this.channelRepository = channelRepository;
        this.telegramBotRepository = telegramBotRepository;
        this.ingestPublisher = ingestPublisher;
    }

    @PostMapping("/vk/{channelId}")
    @Operation(operationId = "hooksVk", summary = "VK Callback API endpoint")
    public ResponseEntity<?> vk(
            @PathVariable UUID channelId,
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Callback-Secret", required = false) String secret) {
        var channel = channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found"));
        String expected = vkSecrets.getOrDefault(channel.getId(), "vk-callback-secret");
        String type = body.path("type").asText("");
        if ("confirmation".equals(type)) {
            return ResponseEntity.ok(body.path("code").asText("ok"));
        }
        String provided = secret != null ? secret : body.path("secret").asText(null);
        if (provided == null || !provided.equals(expected)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid VK callback secret");
        }
        ingestPublisher.publish("vk", channelId.toString(), body);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/tg/{botId}")
    @Operation(operationId = "hooksTg", summary = "Telegram Bot webhook")
    public ResponseEntity<Map<String, Object>> tg(
            @PathVariable UUID botId,
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret) {
        TelegramBotEntity bot = telegramBotRepository
                .findById(botId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Bot not found"));
        if (secret == null || !secret.equals(bot.getWebhookSecret())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid Telegram webhook secret");
        }
        ingestPublisher.publish("tg", botId.toString(), body);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/max/{botId}")
    @Operation(operationId = "hooksMax", summary = "Max Bot webhook")
    public ResponseEntity<Map<String, Object>> max(
            @PathVariable UUID botId,
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Max-Bot-Api-Secret", required = false) String secret) {
        String expected = "local-max-webhook-secret";
        if (secret == null || !secret.equals(expected)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid Max webhook secret");
        }
        ingestPublisher.publish("max", botId.toString(), body);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
