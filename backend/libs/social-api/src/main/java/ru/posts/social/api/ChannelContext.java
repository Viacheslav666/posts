package ru.posts.social.api;

import java.util.UUID;

/** Runtime context for a social API call (tokens decrypted in-memory only). */
public record ChannelContext(
        UUID workspaceId, UUID channelId, String externalId, String accessToken, Network network) {}
