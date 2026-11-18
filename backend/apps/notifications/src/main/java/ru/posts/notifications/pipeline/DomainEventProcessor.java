package ru.posts.notifications.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.messaging.notify.NotifyDeliveryCommand;
import ru.posts.messaging.notify.NotifyDomainEvent;
import ru.posts.notifications.metrics.NotificationMetrics;
import ru.posts.persistence.entity.NotificationDeliveryEntity;
import ru.posts.persistence.entity.NotificationEntity;
import ru.posts.persistence.entity.NotificationSettingsEntity;
import ru.posts.persistence.repository.NotificationDeliveryRepository;
import ru.posts.persistence.repository.NotificationRepository;
import ru.posts.persistence.repository.NotificationSettingsRepository;

@Service
public class DomainEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(DomainEventProcessor.class);

    private final RecipientResolver recipientResolver;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final DeliveryCommandPublisher commandPublisher;
    private final TransactionTemplate transactionTemplate;
    private final NotificationMetrics metrics;

    public DomainEventProcessor(
            RecipientResolver recipientResolver,
            NotificationRepository notificationRepository,
            NotificationDeliveryRepository deliveryRepository,
            NotificationSettingsRepository settingsRepository,
            DeliveryCommandPublisher commandPublisher,
            TransactionTemplate transactionTemplate,
            NotificationMetrics metrics) {
        this.recipientResolver = recipientResolver;
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.settingsRepository = settingsRepository;
        this.commandPublisher = commandPublisher;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    /**
     * Processes a domain event into in-app notifications and channel deliveries.
     *
     * @return {@code true} when the broker message may be acked; {@code false} for poison → nack/DLQ
     */
    public boolean process(NotifyDomainEvent event) {
        Instant started = Instant.now();
        if (event == null || event.eventType() == null || event.eventType().isBlank()) {
            log.warn("Dropping malformed notify domain event");
            return false;
        }
        // notify.events binds `#` and also sees channel commands — ignore those
        if (event.aggregate() == null || event.aggregate().isBlank()) {
            log.debug("Skipping non-domain notify message (missing aggregate)");
            return true;
        }

        try {
            List<UUID> recipients = recipientResolver.resolve(event);
            UUID workspaceId = recipientResolver.resolveWorkspaceId(event);
            if (recipients.isEmpty()) {
                log.info("No recipients for {} aggregate={}", event.eventType(), event.aggregateId());
                metrics.record(event.eventType(), "none", "no_recipients");
                return true;
            }

            for (UUID userId : recipients) {
                processForUser(event, userId, workspaceId);
            }
            metrics.recordProcessing(Duration.between(started, Instant.now()));
            return true;
        } catch (Exception ex) {
            log.error("Failed processing notify domain event {}", event.eventType(), ex);
            metrics.record(event.eventType(), "none", "error");
            return false;
        }
    }

    private void processForUser(NotifyDomainEvent event, UUID userId, UUID workspaceId) {
        String dedupeKey = buildDedupeKey(event.eventType(), event.aggregateId(), userId);
        Map<String, Object> payload = event.payload() == null ? Map.of() : Map.copyOf(event.payload());

        Optional<NotificationEntity> existing = notificationRepository.findByUserIdAndDedupeKey(userId, dedupeKey);
        if (existing.isPresent()) {
            log.debug("Skip duplicate notification dedupeKey={}", dedupeKey);
            metrics.record(event.eventType(), NotificationChannels.IN_APP, "deduped");
            return;
        }

        NotificationSettingsEntity settings =
                settingsRepository.findById(userId).orElse(null);
        ChannelPreference prefs = PreferenceResolver.resolve(settings, event.eventType());

        NotificationEntity notification = transactionTemplate.execute(status -> {
            NotificationEntity created = createNotification(userId, workspaceId, event.eventType(), payload, dedupeKey);
            if (created == null) {
                return null;
            }
            Instant now = Instant.now();
            if (prefs.inApp()) {
                createDelivery(
                        created.getId(),
                        userId,
                        NotificationChannels.IN_APP,
                        event.eventType(),
                        dedupeKey,
                        payload,
                        "sent",
                        now,
                        null);
                metrics.record(event.eventType(), NotificationChannels.IN_APP, "sent");
            }
            for (String channel : NotificationChannels.QUEUE_CHANNELS) {
                if (!prefs.enabled(channel)) {
                    continue;
                }
                Instant delayUntil = QuietHoursEvaluator.delayUntil(settings, event.eventType(), channel, now);
                Instant nextAttempt = delayUntil;
                if (delayUntil != null) {
                    metrics.record(event.eventType(), channel, "quiet_hours_delayed");
                }
                createDelivery(
                        created.getId(),
                        userId,
                        channel,
                        event.eventType(),
                        dedupeKey,
                        payload,
                        "pending",
                        now,
                        nextAttempt);
            }
            return created;
        });

        if (notification == null) {
            metrics.record(event.eventType(), NotificationChannels.IN_APP, "deduped");
            return;
        }

        // Publish due channel commands outside the DB transaction
        Instant now = Instant.now();
        for (String channel : NotificationChannels.QUEUE_CHANNELS) {
            if (!prefs.enabled(channel)) {
                continue;
            }
            deliveryRepository
                    .findByDedupeKeyAndChannel(dedupeKey, channel)
                    .filter(d -> "pending".equals(d.getStatus()))
                    .filter(d -> d.getNextAttemptAt() == null
                            || !d.getNextAttemptAt().isAfter(now))
                    .ifPresent(d -> commandPublisher.publish(toCommand(d, workspaceId, event.traceId())));
        }
    }

    private NotificationEntity createNotification(
            UUID userId, UUID workspaceId, String eventType, Map<String, Object> payload, String dedupeKey) {
        try {
            NotificationEntity entity = new NotificationEntity();
            entity.setUserId(userId);
            entity.setWorkspaceId(workspaceId);
            entity.setType(eventType);
            entity.setPayload(payload);
            entity.setDedupeKey(dedupeKey);
            entity.setCreatedAt(Instant.now());
            return notificationRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Notification dedupe race for {}", dedupeKey);
            return null;
        }
    }

    private NotificationDeliveryEntity createDelivery(
            UUID notificationId,
            UUID userId,
            String channel,
            String eventType,
            String dedupeKey,
            Map<String, Object> payload,
            String status,
            Instant now,
            Instant nextAttemptAt) {
        if (deliveryRepository.existsByDedupeKeyAndChannel(dedupeKey, channel)) {
            return null;
        }
        try {
            NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
            delivery.setNotificationId(notificationId);
            delivery.setUserId(userId);
            delivery.setChannel(channel);
            delivery.setEventType(eventType);
            delivery.setDedupeKey(dedupeKey);
            delivery.setStatus(status);
            delivery.setAttempt(0);
            delivery.setNextAttemptAt(nextAttemptAt);
            delivery.setPayload(enrichPayload(payload, notificationId));
            delivery.setCreatedAt(now);
            delivery.setUpdatedAt(now);
            if ("sent".equals(status)) {
                delivery.setSentAt(now);
            }
            return deliveryRepository.saveAndFlush(delivery);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Delivery dedupe race for {} / {}", dedupeKey, channel);
            return null;
        }
    }

    private static Map<String, Object> enrichPayload(Map<String, Object> payload, UUID notificationId) {
        Map<String, Object> copy = new HashMap<>(payload);
        copy.put("notificationId", notificationId.toString());
        return copy;
    }

    public static String buildDedupeKey(String eventType, UUID aggregateId, UUID userId) {
        return eventType + ":" + (aggregateId == null ? "null" : aggregateId) + ":" + userId;
    }

    public static NotifyDeliveryCommand toCommand(
            NotificationDeliveryEntity delivery, UUID workspaceId, String traceId) {
        return new NotifyDeliveryCommand(
                delivery.getId(),
                delivery.getNotificationId(),
                delivery.getUserId(),
                workspaceId,
                delivery.getChannel(),
                delivery.getEventType(),
                delivery.getDedupeKey(),
                delivery.getPayload(),
                traceId);
    }
}
