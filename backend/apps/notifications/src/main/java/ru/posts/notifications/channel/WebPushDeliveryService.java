package ru.posts.notifications.channel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.posts.messaging.notify.NotifyDeliveryCommand;
import ru.posts.notifications.config.NotificationsProperties;
import ru.posts.persistence.entity.NotificationWebPushSubscriptionEntity;
import ru.posts.persistence.repository.NotificationWebPushSubscriptionRepository;

/**
 * MVP web-push client: HTTP POST JSON body to subscription endpoints with a simplified VAPID-style
 * Authorization header. Full RFC8291 encryption is out of scope for BE-11 MVP.
 */
@Service
public class WebPushDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebPushDeliveryService.class);

    private final NotificationWebPushSubscriptionRepository subscriptionRepository;
    private final NotificationsProperties properties;
    private final RestClient restClient;

    public WebPushDeliveryService(
            NotificationWebPushSubscriptionRepository subscriptionRepository,
            NotificationsProperties properties,
            RestClient.Builder notificationsRestClientBuilder) {
        this.subscriptionRepository = subscriptionRepository;
        this.properties = properties;
        this.restClient = notificationsRestClientBuilder.build();
    }

    public DeliveryOutcome deliver(NotifyDeliveryCommand command) {
        if (!properties.webPush().isConfigured()) {
            return DeliveryOutcome.skipped("vapid_keys_missing");
        }
        List<NotificationWebPushSubscriptionEntity> subscriptions =
                subscriptionRepository.findByUserId(command.userId());
        if (subscriptions.isEmpty()) {
            return DeliveryOutcome.skipped("no_subscriptions");
        }

        int sent = 0;
        String lastError = null;
        for (NotificationWebPushSubscriptionEntity sub : subscriptions) {
            try {
                String body = "{\"title\":\"Posts\",\"body\":\""
                        + escape(command.eventType())
                        + "\",\"data\":"
                        + toJsonish(command.payload())
                        + "}";
                restClient
                        .post()
                        .uri(sub.getEndpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", vapidAuthorization(sub.getEndpoint()))
                        .header("TTL", "60")
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                sub.setLastUsedAt(Instant.now());
                subscriptionRepository.save(sub);
                sent++;
            } catch (RestClientResponseException ex) {
                int code = ex.getStatusCode().value();
                if (code == 404 || code == 410) {
                    log.info("Removing gone web-push subscription {}", sub.getId());
                    subscriptionRepository.delete(sub);
                } else {
                    lastError = "http_" + code;
                    log.warn("Web-push HTTP {} for {}", code, command.deliveryId());
                }
            } catch (Exception ex) {
                lastError = ex.getMessage();
                log.warn("Web-push failed for {}: {}", command.deliveryId(), ex.toString());
            }
        }
        if (sent > 0) {
            return DeliveryOutcome.sent("web_push:" + sent);
        }
        if (lastError == null) {
            return DeliveryOutcome.skipped("all_subscriptions_gone");
        }
        return DeliveryOutcome.failed(lastError);
    }

    private String vapidAuthorization(String audience) {
        // Simplified MVP header — not a signed JWT. Enough for WireMock / local stubs.
        String subject = properties.webPush().vapidSubject();
        String pub = properties.webPush().vapidPublicKey();
        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((audience + "|" + subject + "|" + pub).getBytes(StandardCharsets.UTF_8));
        return "vapid t=" + payload + ", k=" + pub;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toJsonish(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"')
                    .append(escape(e.getKey()))
                    .append("\":\"")
                    .append(escape(e.getValue() == null ? "" : String.valueOf(e.getValue())))
                    .append('"');
        }
        sb.append('}');
        return sb.toString();
    }
}
