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
            Instant createdAt) {}

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
    public record UpdateChannelRequest(UUID projectId, @Schema(example = "Europe/Moscow") String timezone) {}

    @Schema(name = "ChannelConnectCallbackRequest")
    public record ChannelConnectCallbackRequest(
            @NotBlank String code, @NotBlank String state, @NotNull UUID workspaceId, UUID projectId) {}
}
