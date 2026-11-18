package ru.posts.api.web.dto.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.posts.api.web.dto.common.ApiEnums;

public final class ChannelDtos {

    private ChannelDtos() {}

    @Schema(name = "Channel")
    public record Channel(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            ApiEnums.Network network,
            String externalId,
            ApiEnums.ChannelType type,
            @Schema(example = "Новости бренда") String title,
            String avatarUrl,
            String username,
            ApiEnums.ChannelStatus status,
            String statusReason,
            String timezone,
            Map<String, Object> capabilities,
            Instant createdAt,
            @Schema(description = "VK stories connect status; OK does not require a separate connect")
                    ApiEnums.StoryConnectionStatus storyConnectionStatus,
            @Schema(nullable = true) Instant storyConnectedAt,
            @Schema(nullable = true) String storyConnectionError,
            @Schema(description = "Telegram forum topic id (message_thread_id)", nullable = true) String threadId,
            @Schema(description = "True when a custom bot token is configured for this channel")
                    boolean customBotToken) {}

    @Schema(name = "StoryConnection")
    public record StoryConnection(
            UUID channelId,
            ApiEnums.Network network,
            ApiEnums.StoryConnectionStatus status,
            @Schema(nullable = true) String authorizationUrl,
            @Schema(nullable = true) Instant connectedAt,
            @Schema(nullable = true) String error) {}

    @Schema(name = "StartStoryConnectionRequest")
    public record StartStoryConnectionRequest(
            @Schema(description = "When true (local/emulator), mark stories connection ready without OAuth redirect")
                    Boolean confirmReady) {}

    @Schema(name = "ChannelConnectStartResponse")
    public record ChannelConnectStartResponse(
            @Schema(example = "https://oauth.example/...") String authorizationUrl,
            String state,
            @Schema(description = "Bot deep-link for tg/max when applicable", nullable = true) String botInviteUrl) {}

    @Schema(name = "ChannelCheckResponse")
    public record ChannelCheckResponse(
            UUID channelId, ApiEnums.ChannelStatus status, String statusReason, Instant checkedAt) {}

    @Schema(name = "ChannelSet")
    public record ChannelSet(
            UUID id, UUID workspaceId, @Schema(example = "Утренние посты") String name, List<UUID> channelIds) {}

    @Schema(name = "CreateChannelSetRequest")
    public record CreateChannelSetRequest(
            @NotBlank @Size(min = 1, max = 120) String name, @NotEmpty @Size(max = 50) List<UUID> channelIds) {}

    @Schema(name = "UpdateChannelSetRequest")
    public record UpdateChannelSetRequest(
            @Size(min = 1, max = 120) String name, @Size(max = 50) List<UUID> channelIds) {}

    @Schema(name = "UpdateChannelRequest")
    public record UpdateChannelRequest(
            UUID projectId,
            @Schema(example = "Europe/Moscow") String timezone,
            @Schema(description = "Telegram forum topic id (message_thread_id)", nullable = true) String threadId,
            @Schema(
                            description =
                                    "Optional custom Telegram/Max bot token (business.max.ru / BotFather); sealed at rest",
                            nullable = true)
                    String botToken) {}

    @Schema(
            name = "ChannelConnectCallbackRequest",
            description = "OAuth callback (VK/OK) or bot-flow confirm (TG/Max). For OAuth provide code+state; "
                    + "for bot flows provide username and/or forwardedMessage/chatId (US-3.2/3.4).")
    public record ChannelConnectCallbackRequest(
            @Schema(description = "OAuth authorization code (VK/OK)") String code,
            @Schema(description = "OAuth state (VK/OK)") String state,
            @NotNull UUID workspaceId,
            UUID projectId,
            @Schema(description = "Telegram/Max channel or chat @username", example = "@brand_news") String username,
            @Schema(description = "External chat/channel id when known") String chatId,
            @Schema(description = "Forwarded message reference for bot attach flows")
                    ForwardedMessageRef forwardedMessage,
            @Schema(description = "Optional custom bot token (Telegram BotFather or Max business.max.ru)")
                    String botToken,
            @Schema(description = "Telegram forum topic id (message_thread_id)", example = "42") String threadId) {}

    @Schema(name = "ForwardedMessageRef")
    public record ForwardedMessageRef(
            @Schema(example = "-1001234567890") String chatId, @Schema(example = "42") String messageId) {}

    @Schema(
            name = "ChannelConnectConfirmRequest",
            description = "Dedicated bot-flow confirm for Telegram/Max after the bot is added as admin")
    public record ChannelConnectConfirmRequest(
            @NotNull UUID workspaceId,
            UUID projectId,
            @Schema(example = "@brand_news") String username,
            String chatId,
            ForwardedMessageRef forwardedMessage,
            @Schema(description = "Optional custom bot token (Telegram BotFather or Max business.max.ru)")
                    String botToken,
            @Schema(description = "Telegram forum topic id (message_thread_id)", example = "42") String threadId) {}
}
