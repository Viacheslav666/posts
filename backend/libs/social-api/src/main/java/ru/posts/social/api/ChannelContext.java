package ru.posts.social.api;

import java.util.UUID;

/** Runtime context for a social API call (tokens decrypted in-memory only). */
public record ChannelContext(
        UUID workspaceId,
        UUID channelId,
        String externalId,
        String accessToken,
        Network network,
        /** Telegram forum topic id ({@code message_thread_id}); null when not a topic. */
        String messageThreadId) {

    public ChannelContext(UUID workspaceId, UUID channelId, String externalId, String accessToken, Network network) {
        this(workspaceId, channelId, externalId, accessToken, network, null);
    }
}
