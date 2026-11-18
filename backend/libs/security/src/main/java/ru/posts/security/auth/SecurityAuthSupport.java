package ru.posts.security.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class SecurityAuthSupport {

    public static final String CLAIM_IMPERSONATING = "imp";
    public static final String CLAIM_ACTOR = "act";
    public static final String CLAIM_IMPERSONATION_REASON = "imp_reason";

    private SecurityAuthSupport() {}

    public static Optional<PostsPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return Optional.empty();
        }
        Jwt jwt = jwtAuth.getToken();
        UUID userId = UUID.fromString(jwt.getSubject());
        String sid = jwt.getClaimAsString("sid");
        UUID sessionId = sid == null || sid.isBlank() ? null : UUID.fromString(sid);
        boolean impersonating = Boolean.TRUE.equals(jwt.getClaim(CLAIM_IMPERSONATING));
        UUID actorUserId = null;
        String act = jwt.getClaimAsString(CLAIM_ACTOR);
        if (act != null && !act.isBlank()) {
            actorUserId = UUID.fromString(act);
        }
        String reason = jwt.getClaimAsString(CLAIM_IMPERSONATION_REASON);
        Instant expiresAt = jwt.getExpiresAt();
        return Optional.of(new PostsPrincipal(userId, sessionId, impersonating, actorUserId, reason, expiresAt));
    }

    public static PostsPrincipal requirePrincipal() {
        return currentPrincipal().orElseThrow(() -> new IllegalStateException("Unauthenticated"));
    }
}
