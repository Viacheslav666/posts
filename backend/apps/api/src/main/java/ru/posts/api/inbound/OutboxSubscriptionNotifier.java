package ru.posts.api.inbound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.repository.OutboxRepository;
import ru.posts.social.inbound.subscription.SubscriptionNotifier;

@Component
@Profile("!openapi")
public class OutboxSubscriptionNotifier implements SubscriptionNotifier {

    private final OutboxRepository outboxRepository;

    public OutboxSubscriptionNotifier(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void notifyDegraded(
            UUID workspaceId, UUID channelId, String network, String message, Map<String, Object> details) {
        OutboxEntity event = new OutboxEntity();
        event.setAggregate("channel");
        event.setAggregateId(channelId);
        event.setEventType("channel.event_subscription_degraded");
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceId", workspaceId.toString());
        payload.put("channelId", channelId.toString());
        payload.put("network", network);
        payload.put("message", message);
        if (details != null) {
            payload.putAll(details);
        }
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
