package ru.posts.security.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class SecurityAuthSupport {

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
        return Optional.of(new PostsPrincipal(userId, sessionId));
    }

    public static PostsPrincipal requirePrincipal() {
        return currentPrincipal().orElseThrow(() -> new IllegalStateException("Unauthenticated"));
    }
}
