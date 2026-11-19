package ru.posts.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.channel.IngestBrokerUnavailableException;
import ru.posts.api.channel.IngestPublisher;
import ru.posts.api.channel.RabbitIngestPublisher;
import ru.posts.api.channel.WorkspaceTokenVault;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.notification.NotificationService;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.ChannelEventSubscriptionEntity;
import ru.posts.persistence.entity.TelegramBotEntity;
import ru.posts.persistence.repository.ChannelEventSubscriptionRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.TelegramBotRepository;
import ru.posts.social.inbound.metrics.SocialInboundMetrics;

/**
 * Social inbound webhooks: validate secret quickly and publish raw payload to ingest (BE-32).
 * VK/Max confirmation and auth happen here; heavy work is in posts-ingest.
 */
@RestController
@Profile("!openapi")
@RequestMapping(path = "/hooks")
@Tag(name = "Social hooks", description = "Inbound webhooks for VK / Telegram / Max (validate + enqueue)")
public class SocialHooksController {

    private final ChannelRepository channelRepository;
    private final ChannelEventSubscriptionRepository subscriptionRepository;
    private final TelegramBotRepository telegramBotRepository;
    private final IngestPublisher ingestPublisher;
    private final NotificationService notificationService;
    private final WorkspaceTokenVault tokenVault;
    private final SocialInboundMetrics metrics;

    public SocialHooksController(
            ChannelRepository channelRepository,
            ChannelEventSubscriptionRepository subscriptionRepository,
            TelegramBotRepository telegramBotRepository,
            IngestPublisher ingestPublisher,
            NotificationService notificationService,
            WorkspaceTokenVault tokenVault,
            SocialInboundMetrics metrics) {
        this.channelRepository = channelRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.telegramBotRepository = telegramBotRepository;
        this.ingestPublisher = ingestPublisher;
        this.notificationService = notificationService;
        this.tokenVault = tokenVault;
        this.metrics = metrics;
    }

    @PostMapping(path = "/vk/{channelId}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(operationId = "hooksVk", summary = "VK Callback API endpoint")
    public ResponseEntity<String> vk(
            @PathVariable UUID channelId,
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Retry-Counter", required = false) Integer retryCounter) {
        ChannelEntity channel = channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found"));
        ChannelEventSubscriptionEntity sub =
                subscriptionRepository.findByChannelId(channelId).orElse(null);

        String type = body.path("type").asText("");
        if ("confirmation".equals(type)) {
            String code = sub != null ? sub.getConfirmationCode() : null;
            if (code == null || code.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Confirmation code not ready");
            }
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(code);
        }

        String provided = body.path("secret").asText(null);
        String expected = resolveSecret(channel, sub);
        if (!constantTimeEquals(expected, provided)) {
            metrics.recordAuthFailure("vk");
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid VK callback secret");
        }

        String groupId = body.path("group_id").asText(null);
        if (groupId != null && channel.getExternalId() != null) {
            String expectedGroup = channel.getExternalId().startsWith("-")
                    ? channel.getExternalId().substring(1)
                    : channel.getExternalId();
            if (!expectedGroup.equals(groupId) && !channel.getExternalId().equals(groupId)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "group_id mismatch");
            }
        }

        try {
            publish("vk", channelId.toString(), body, retryCounter);
        } catch (IngestBrokerUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "10")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("unavailable");
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("ok");
    }

    @PostMapping(path = "/tg/{botId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "hooksTg", summary = "Telegram Bot webhook")
    public ResponseEntity<?> tg(
            @PathVariable UUID botId,
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret) {
        TelegramBotEntity bot = telegramBotRepository
                .findById(botId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Bot not found"));
        if (!constantTimeEquals(bot.getWebhookSecret(), secret)) {
            metrics.recordAuthFailure("tg");
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid Telegram webhook secret");
        }
        try {
            publish("tg", botId.toString(), body, null);
        } catch (IngestBrokerUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "10")
                    .body(Map.of("ok", false, "error", "unavailable"));
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping(path = "/max/{channelId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "hooksMax", summary = "Max Bot webhook")
    public ResponseEntity<?> max(
            @PathVariable UUID channelId,
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Max-Bot-Api-Secret", required = false) String secret) {
        ChannelEntity channel = channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found"));
        ChannelEventSubscriptionEntity sub =
                subscriptionRepository.findByChannelId(channelId).orElse(null);
        String expected = resolveSecret(channel, sub);
        if (!constantTimeEquals(expected, secret)) {
            metrics.recordAuthFailure("max");
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid Max webhook secret");
        }
        try {
            publish("max", channelId.toString(), body, null);
        } catch (IngestBrokerUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "10")
                    .body(Map.of("ok", false, "error", "unavailable"));
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping(path = "/telegram-notify", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "hooksTelegramNotify",
            summary = "Telegram notification-bot webhook (account linking via /start CODE)")
    public ResponseEntity<Map<String, Object>> telegramNotify(
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret) {
        return ResponseEntity.ok(notificationService.handleTelegramNotifyWebhook(body, secret));
    }

    private void publish(String network, String resourceId, JsonNode body, Integer retryCounter) {
        if (ingestPublisher instanceof RabbitIngestPublisher rabbit) {
            rabbit.publish(network, resourceId, body, retryCounter);
        } else {
            ingestPublisher.publish(network, resourceId, body);
        }
    }

    private String resolveSecret(ChannelEntity channel, ChannelEventSubscriptionEntity sub) {
        if (sub != null && sub.getSecretEnc() != null) {
            String unsealed = tokenVault.unseal(channel.getWorkspaceId(), sub.getSecretEnc());
            if (unsealed != null) {
                return unsealed;
            }
        }
        return null;
    }

    static boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
