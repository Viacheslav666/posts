package ru.posts.grabber.token;

import java.util.Optional;
import java.util.UUID;

/** Resolves an access token for reading VK source walls. */
public interface GrabberTokenProvider {

    Optional<String> resolveVkToken(UUID workspaceId, UUID targetChannelId);
}
