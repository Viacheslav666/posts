package ru.posts.notifications.channel;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.notifications.config.NotificationsProperties;
import ru.posts.notifications.metrics.NotificationMetrics;
import ru.posts.notifications.pipeline.DeliveryBackoff;
import ru.posts.persistence.entity.NotificationDeliveryEntity;
import ru.posts.persistence.repository.NotificationDeliveryRepository;

@Component
public class DeliveryStatusUpdater {

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationsProperties properties;
    private final NotificationMetrics metrics;

    public DeliveryStatusUpdater(
            NotificationDeliveryRepository deliveryRepository,
            NotificationsProperties properties,
            NotificationMetrics metrics) {
        this.deliveryRepository = deliveryRepository;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Transactional
    public void apply(NotificationDeliveryEntity delivery, DeliveryOutcome outcome) {
        Instant now = Instant.now();
        delivery.setAttempt(delivery.getAttempt() + 1);
        delivery.setUpdatedAt(now);
        switch (outcome.status()) {
            case SENT -> {
                delivery.setStatus("sent");
                delivery.setSentAt(now);
                delivery.setProviderMessageId(outcome.providerMessageId());
                delivery.setLastError(null);
                delivery.setNextAttemptAt(null);
                metrics.record(delivery.getEventType(), delivery.getChannel(), "sent");
            }
            case SKIPPED -> {
                delivery.setStatus("skipped");
                delivery.setLastError(outcome.error());
                delivery.setNextAttemptAt(null);
                metrics.record(delivery.getEventType(), delivery.getChannel(), "skipped");
            }
            case FAILED -> {
                if (DeliveryBackoff.exhausted(delivery.getAttempt(), properties.maxAttempts())) {
                    delivery.setStatus("dead");
                    delivery.setLastError(outcome.error());
                    delivery.setNextAttemptAt(null);
                    metrics.record(delivery.getEventType(), delivery.getChannel(), "dead");
                } else {
                    delivery.setStatus("failed");
                    delivery.setLastError(outcome.error());
                    delivery.setNextAttemptAt(DeliveryBackoff.nextAttemptAt(delivery.getAttempt(), now));
                    metrics.record(delivery.getEventType(), delivery.getChannel(), "failed");
                }
            }
        }
        deliveryRepository.save(delivery);
    }
}
