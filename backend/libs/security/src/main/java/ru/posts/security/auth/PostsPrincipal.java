package ru.posts.security.auth;

import java.time.Instant;
import java.util.UUID;

/** Authenticated API principal extracted from the access JWT. */
public record PostsPrincipal(
        UUID userId,
        UUID sessionId,
        boolean impersonating,
        UUID actorUserId,
        String impersonationReason,
        Instant expiresAt) {

    /** Convenience constructor for non-impersonation sessions (tests / stubs). */
    public PostsPrincipal(UUID userId, UUID sessionId) {
        this(userId, sessionId, false, null, null, null);
    }

    public UUID effectiveActorId() {
        return impersonating && actorUserId != null ? actorUserId : userId;
    }
}
