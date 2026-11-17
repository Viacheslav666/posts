package ru.posts.api.web.dto.network;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import ru.posts.api.web.dto.common.ApiEnums;

public final class NetworkDtos {

    private NetworkDtos() {}

    @Schema(
            name = "NetworkCapabilitiesResponse",
            description = "Snapshot of network limits/features for client-side validation (AN-2)")
    public record NetworkCapabilitiesResponse(
            @Schema(example = "1.0.0") String version,
            @Schema(example = "2026-11-17") String checkedAt,
            List<NetworkCapability> networks) {}

    @Schema(name = "NetworkCapability")
    public record NetworkCapability(
            ApiEnums.Network id,
            @Schema(example = "ВКонтакте") String name,
            @Schema(example = "mvp") String phase,
            List<String> channelTypes,
            List<String> contentTypes,
            TextLimits text,
            FormattingCapability formatting,
            MediaLimits media,
            FeatureFlags features,
            RateLimits rateLimits) {}

    @Schema(name = "TextLimits")
    public record TextLimits(
            @Schema(
                            description = "Max post text length; null when officially unknown",
                            nullable = true,
                            example = "4096")
                    Integer maxChars,
            @Schema(description = "Max caption length when media is attached", nullable = true, example = "1024")
                    Integer captionMaxChars) {}

    @Schema(name = "FormattingCapability")
    public record FormattingCapability(
            @Schema(example = "html") String mode, List<String> features, List<String> htmlTags) {}

    @Schema(name = "MediaLimits")
    public record MediaLimits(
            @Schema(nullable = true, example = "10") Integer maxAttachmentsPerPost,
            Map<String, Object> photo,
            Map<String, Object> video) {}

    @Schema(name = "FeatureFlags")
    public record FeatureFlags(
            boolean pin,
            boolean unpin,
            boolean deleteAfterPublish,
            boolean firstComment,
            boolean inlineButtons,
            boolean nativeSchedule,
            boolean stories) {}

    @Schema(name = "RateLimits")
    public record RateLimits(
            @Schema(nullable = true) Double globalRps,
            @Schema(nullable = true) Double perTokenRps,
            @Schema(nullable = true) Double perChatRps,
            Map<String, Object> extra) {}
}
