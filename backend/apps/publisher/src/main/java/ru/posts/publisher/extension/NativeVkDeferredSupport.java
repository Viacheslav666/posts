package ru.posts.publisher.extension;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.posts.persistence.jdbc.PublicationClaim;

/**
 * Extension point for §5.6 native VK deferred posting (Phase 2 / ADR). Disabled by default —
 * Posts publishes via its own scheduler for unified retries/status. When enabled later, this
 * component (or a replacement bean) can hand off to VK {@code publish_date}.
 */
@Component
@ConditionalOnProperty(name = "posts.publisher.vk.native-deferred.enabled", havingValue = "true")
public class NativeVkDeferredSupport {

    public boolean shouldUseNativeDeferred(PublicationClaim claim) {
        return "vk".equals(claim.network())
                && claim.runAt().isAfter(java.time.Instant.now().plusSeconds(60));
    }
}
