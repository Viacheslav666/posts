package ru.posts.api.web.dto.crosspost;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CrosspostDtos {
    private CrosspostDtos() {}

    @Schema(name = "CrosspostRuleStatus")
    public enum RuleStatus {
        draft,
        active,
        paused,
        auto_paused
    }

    @Schema(name = "CrosspostItemStatus")
    public enum ItemStatus {
        received,
        filtered_out,
        skipped_own,
        skipped_loop,
        skipped_duplicate,
        skipped_unsupported_media,
        queued,
        published,
        partially_published,
        failed
    }

    @Schema(name = "CrosspostTargetRequest")
    public record TargetRequest(@NotNull UUID targetChannelId, Map<String, Object> options) {}

    @Schema(name = "CrosspostRuleCreate")
    public record RuleCreate(
            @NotBlank String name,
            @NotNull UUID sourceChannelId,
            Map<String, Object> config,
            List<TargetRequest> targets,
            Boolean activate) {}

    @Schema(name = "CrosspostRulePatch")
    public record RulePatch(Integer version, String name, Map<String, Object> config, List<TargetRequest> targets) {}

    @Schema(name = "CrosspostWarning")
    public record Warning(String code, String message, List<UUID> channelPath) {}

    @Schema(name = "CrosspostTarget")
    public record Target(UUID id, UUID targetChannelId, String network, Map<String, Object> options) {}

    @Schema(name = "CrosspostRule")
    public record Rule(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            UUID sourceChannelId,
            String sourceNetwork,
            String name,
            RuleStatus status,
            Map<String, Object> config,
            int configVersion,
            int version,
            List<Target> targets,
            List<Warning> warnings,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "CrosspostRuleList")
    public record RuleList(List<Rule> items) {}

    @Schema(name = "CrosspostPreviewRequest")
    public record PreviewRequest(String sourcePostUrl, String sourceRemoteId, Map<String, Object> candidate) {}

    @Schema(name = "CrosspostPreviewTarget")
    public record PreviewTarget(
            UUID targetChannelId, String network, String status, String reason, Map<String, Object> variant) {}

    @Schema(name = "CrosspostPreviewResponse")
    public record PreviewResponse(List<String> filterReasons, List<PreviewTarget> targets) {}

    @Schema(name = "CrosspostItem")
    public record Item(
            UUID id,
            UUID ruleId,
            UUID sourceChannelId,
            String sourceRemoteId,
            String sourceNetwork,
            ItemStatus status,
            List<String> reasons,
            UUID postId,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "CrosspostItemPage")
    public record ItemPage(List<Item> items, String nextCursor) {}
}
