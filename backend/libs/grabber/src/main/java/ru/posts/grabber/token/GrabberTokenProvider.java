package ru.posts.grabber.token;

import java.util.Optional;
import java.util.UUID;

/** Resolves an access token for reading VK source walls. */
public interface GrabberTokenProvider {

    Optional<String> resolveVkToken(UUID workspaceId, UUID targetChannelId);

    /**
     * Token for media uploads into the target community (VK upload methods need the admin's user
     * token). Defaults to the wall-reading token.
     */
    default Optional<String> resolveTargetUploadToken(UUID workspaceId, UUID targetChannelId) {
        return resolveVkToken(workspaceId, targetChannelId);
    }
}
