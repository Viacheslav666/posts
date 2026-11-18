package ru.posts.ord.reporting;

import java.util.Optional;
import java.util.UUID;

/** Resolves social channel access tokens for impression fetches. */
@FunctionalInterface
public interface OrdChannelTokenResolver {

    Optional<String> resolveAccessToken(UUID workspaceId, UUID channelId);
}
