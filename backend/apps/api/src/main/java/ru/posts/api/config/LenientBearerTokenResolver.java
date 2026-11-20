package ru.posts.api.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Treats malformed or expired Bearer tokens as absent so {@code permitAll} routes (e.g. {@code /auth/register})
 * still work when the browser reuses a stale access JWT on the same request.
 */
public final class LenientBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    private final JwtDecoder jwtDecoder;

    public LenientBearerTokenResolver(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String token = delegate.resolve(request);
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            jwtDecoder.decode(token);
            return token;
        } catch (JwtException ex) {
            return null;
        }
    }
}
