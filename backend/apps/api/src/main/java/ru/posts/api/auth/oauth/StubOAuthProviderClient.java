package ru.posts.api.auth.oauth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;

/**
 * Deterministic OAuth client for local/test. Authorization codes are registered via
 * {@link #registerCode}.
 */
public class StubOAuthProviderClient implements OAuthProviderClient {

    private final ConcurrentHashMap<String, OAuthUserProfile> codes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OAuthCodeExchange> exchanges = new ConcurrentHashMap<>();

    public void registerCode(String code, OAuthUserProfile profile) {
        codes.put(code, profile);
    }

    /** Last exchange request seen for {@code code}, or {@code null}. */
    public OAuthCodeExchange exchangeFor(String code) {
        return exchanges.get(code);
    }

    public void clear() {
        codes.clear();
        exchanges.clear();
    }

    @Override
    public OAuthUserProfile exchange(String provider, OAuthCodeExchange exchange) {
        OAuthUserProfile profile = codes.get(exchange.code());
        if (profile == null || !provider.equals(profile.provider())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid OAuth code");
        }
        exchanges.put(exchange.code(), exchange);
        return new OAuthUserProfile(
                profile.provider(),
                profile.providerUserId(),
                profile.email(),
                profile.emailVerified(),
                profile.name(),
                profile.raw() == null ? Map.of() : profile.raw());
    }
}
