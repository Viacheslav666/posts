package ru.posts.grabber.token;

import java.util.Optional;
import java.util.UUID;

/** Fallback token provider for local/tests. Production API may override with vault-backed bean. */
public class StaticGrabberTokenProvider implements GrabberTokenProvider {

    private final String token;

    public StaticGrabberTokenProvider(String token) {
        this.token = token;
    }

    @Override
    public Optional<String> resolveVkToken(UUID workspaceId, UUID targetChannelId) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(token);
    }
}
