package ru.posts.api.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.BillingWebhookInboxEntity;
import ru.posts.persistence.repository.BillingWebhookInboxRepository;

@Service
@Profile("!openapi")
public class BillingWebhookService {

    private static final Logger log = LoggerFactory.getLogger(BillingWebhookService.class);

    private final BillingWebhookInboxRepository inboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public BillingWebhookService(
            BillingWebhookInboxRepository inboxRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.inboxRepository = inboxRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists the webhook to the inbox (idempotent by event_id) and schedules processing after
     * commit. Always safe to acknowledge with HTTP 200 after this returns.
     *
     * @return true if a new inbox row was inserted
     */
    @Transactional
    public boolean ingest(JsonNode body) {
        String eventType = text(body, "event");
        JsonNode object = body.path("object");
        String providerPaymentId = text(object, "id");
        if (eventType == null || eventType.isBlank()) {
            log.warn("YooKassa webhook missing event type");
            return false;
        }
        String eventId = eventType + ":" + (providerPaymentId == null ? UuidV7.generate() : providerPaymentId);

        if (inboxRepository.findByEventId(eventId).isPresent()) {
            return false;
        }

        BillingWebhookInboxEntity inbox = new BillingWebhookInboxEntity();
        inbox.setId(UuidV7.generate());
        inbox.setEventId(eventId);
        inbox.setEventType(eventType);
        inbox.setProviderPaymentId(providerPaymentId);
        inbox.setPayload(toMap(body));
        inbox.setStatus("pending");
        inbox.setReceivedAt(Instant.now());
        try {
            inboxRepository.saveAndFlush(inbox);
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
        eventPublisher.publishEvent(new YooKassaWebhookReceivedEvent(inbox.getId()));
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode body) {
        try {
            return objectMapper.convertValue(body, Map.class);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("raw", body.toString());
            return fallback;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }
}
