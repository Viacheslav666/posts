package ru.posts.persistence.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.persistence.jdbc.SocialRemoteActionLifecycleJdbc;

/**
 * Module-facing API to enqueue remote moderation actions (BE-33).
 *
 * <p>Must be called inside the caller's transaction (default {@code REQUIRED}) so domain writes and
 * the action row commit together.
 */
public class RemoteActionService {

    private final SocialRemoteActionLifecycleJdbc lifecycleJdbc;

    public RemoteActionService(SocialRemoteActionLifecycleJdbc lifecycleJdbc) {
        this.lifecycleJdbc = lifecycleJdbc;
    }

    @Transactional
    public UUID enqueue(
            String kind,
            UUID workspaceId,
            UUID channelId,
            String targetJson,
            String payloadJson,
            String idempotencyKey,
            Instant runAt,
            String source,
            String sourceRefJson,
            UUID createdBy) {
        return lifecycleJdbc.enqueue(
                kind,
                workspaceId,
                channelId,
                targetJson,
                payloadJson,
                idempotencyKey,
                runAt,
                source,
                sourceRefJson,
                createdBy);
    }
}
