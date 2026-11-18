package ru.posts.notifications.channel;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.posts.messaging.notify.NotifyDeliveryCommand;
import ru.posts.notifications.config.NotificationsProperties;
import ru.posts.persistence.entity.NotificationTelegramLinkEntity;
import ru.posts.persistence.repository.NotificationTelegramLinkRepository;

@Service
public class TelegramDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(TelegramDeliveryService.class);

    private final NotificationTelegramLinkRepository linkRepository;
    private final NotificationsProperties properties;
    private final RestClient restClient;
    private final AtomicLong lastSendEpochMs = new AtomicLong(0);

    public TelegramDeliveryService(
            NotificationTelegramLinkRepository linkRepository,
            NotificationsProperties properties,
            RestClient.Builder notificationsRestClientBuilder) {
        this.linkRepository = linkRepository;
        this.properties = properties;
        this.restClient = notificationsRestClientBuilder.build();
    }

    public DeliveryOutcome deliver(NotifyDeliveryCommand command) {
        String token = properties.telegram().botToken();
        if (token == null || token.isBlank()) {
            return DeliveryOutcome.skipped("telegram_bot_token_missing");
        }
        NotificationTelegramLinkEntity link =
                linkRepository.findById(command.userId()).orElse(null);
        if (link == null || !link.isLinked() || link.getChatId() == null) {
            return DeliveryOutcome.skipped("telegram_not_linked");
        }

        throttle();
        String text = formatMessage(command);
        String url = properties.telegram().apiBaseUrl() + "/bot" + token + "/sendMessage";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", link.getChatId(), "text", text, "disable_web_page_preview", true))
                    .retrieve()
                    .body(Map.class);
            Object result = response == null ? null : response.get("result");
            String messageId = null;
            if (result instanceof Map<?, ?> map && map.get("message_id") != null) {
                messageId = String.valueOf(map.get("message_id"));
            }
            return DeliveryOutcome.sent(messageId == null ? "tg:ok" : "tg:" + messageId);
        } catch (RestClientResponseException ex) {
            log.warn("Telegram sendMessage HTTP {} for {}", ex.getStatusCode().value(), command.deliveryId());
            return DeliveryOutcome.failed("http_" + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("Telegram delivery failed for {}: {}", command.deliveryId(), ex.toString());
            return DeliveryOutcome.failed(ex.getMessage());
        }
    }

    private void throttle() {
        Duration minInterval = properties.rateLimits().telegramMinInterval();
        if (minInterval == null || minInterval.isZero() || minInterval.isNegative()) {
            return;
        }
        long minGap = minInterval.toMillis();
        while (true) {
            long now = System.currentTimeMillis();
            long last = lastSendEpochMs.get();
            long wait = last + minGap - now;
            if (wait <= 0 && lastSendEpochMs.compareAndSet(last, now)) {
                return;
            }
            try {
                Thread.sleep(Math.max(1, wait));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static String formatMessage(NotifyDeliveryCommand command) {
        StringBuilder sb = new StringBuilder();
        sb.append("Posts: ").append(command.eventType());
        if (command.payload() != null) {
            Object network = command.payload().get("network");
            Object result = command.payload().get("result");
            if (network != null) {
                sb.append("\nСеть: ").append(network);
            }
            if (result != null) {
                sb.append("\nРезультат: ").append(result);
            }
        }
        return sb.toString();
    }
}
