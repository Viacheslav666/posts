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
        String messageThreadId,
        /** channels.type: group, page, profile, channel, chat. */
        String channelType,
        /** channels.status: active, reauth_required, no_permissions, disabled. */
        String channelStatus) {

    public ChannelContext(UUID workspaceId, UUID channelId, String externalId, String accessToken, Network network) {
        this(workspaceId, channelId, externalId, accessToken, network, null, null, null);
    }

    public ChannelContext(
            UUID workspaceId,
            UUID channelId,
            String externalId,
            String accessToken,
            Network network,
            String messageThreadId) {
        this(workspaceId, channelId, externalId, accessToken, network, messageThreadId, null, null);
    }
}
