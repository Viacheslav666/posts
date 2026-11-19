package ru.posts.social.inbound.subscription;

import java.util.Map;
import java.util.UUID;

/** Notifies workspace about subscription health issues (via outbox / NotificationService). */
public interface SubscriptionNotifier {
    void notifyDegraded(UUID workspaceId, UUID channelId, String network, String message, Map<String, Object> details);
}
