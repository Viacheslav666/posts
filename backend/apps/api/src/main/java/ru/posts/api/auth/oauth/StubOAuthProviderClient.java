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

    public void registerCode(String code, OAuthUserProfile profile) {
        codes.put(code, profile);
    }

    public void clear() {
        codes.clear();
    }

    @Override
    public OAuthUserProfile exchange(String provider, String code, String codeVerifier, String redirectUri) {
        OAuthUserProfile profile = codes.get(code);
        if (profile == null || !provider.equals(profile.provider())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid OAuth code");
        }
        return new OAuthUserProfile(
                profile.provider(),
                profile.providerUserId(),
                profile.email(),
                profile.emailVerified(),
                profile.name(),
                profile.raw() == null ? Map.of() : profile.raw());
    }
}
