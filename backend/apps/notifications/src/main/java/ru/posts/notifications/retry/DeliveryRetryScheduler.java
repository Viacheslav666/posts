package ru.posts.notifications.retry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.messaging.notify.NotifyDeliveryCommand;
import ru.posts.notifications.config.NotificationsProperties;
import ru.posts.notifications.pipeline.DeliveryCommandPublisher;
import ru.posts.notifications.pipeline.DomainEventProcessor;
import ru.posts.notifications.pipeline.NotificationChannels;
import ru.posts.persistence.entity.NotificationDeliveryEntity;
import ru.posts.persistence.repository.NotificationDeliveryRepository;

@Component
public class DeliveryRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRetryScheduler.class);

    private final NotificationDeliveryRepository deliveryRepository;
    private final DeliveryCommandPublisher commandPublisher;
    private final NotificationsProperties properties;

    public DeliveryRetryScheduler(
            NotificationDeliveryRepository deliveryRepository,
            DeliveryCommandPublisher commandPublisher,
            NotificationsProperties properties) {
        this.deliveryRepository = deliveryRepository;
        this.commandPublisher = commandPublisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${posts.notifications.retry-poll-interval-ms:2000}")
    public void republishDue() {
        Instant now = Instant.now();
        List<NotificationDeliveryEntity> due =
                deliveryRepository.findDueDeliveries(now, PageRequest.of(0, properties.retryBatchSize()));
        if (due.isEmpty()) {
            return;
        }
        for (NotificationDeliveryEntity delivery : due) {
            if (!NotificationChannels.QUEUE_CHANNELS.contains(delivery.getChannel())) {
                continue;
            }
            if ("sent".equals(delivery.getStatus())
                    || "skipped".equals(delivery.getStatus())
                    || "dead".equals(delivery.getStatus())) {
                continue;
            }
            UUID workspaceId = extractWorkspaceId(delivery.getPayload());
            NotifyDeliveryCommand command = DomainEventProcessor.toCommand(delivery, workspaceId, null);
            try {
                commandPublisher.publish(command);
            } catch (Exception ex) {
                log.warn("Failed to re-publish delivery {}: {}", delivery.getId(), ex.toString());
            }
        }
    }

    private static UUID extractWorkspaceId(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get("workspaceId");
        if (value == null) {
            value = payload.get("workspace_id");
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
