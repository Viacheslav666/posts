package ru.posts.api.web.dto.ingest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** OpenAPI DTOs for BE-17 Grabber / RSS Ingest. */
public final class IngestDtos {

    private IngestDtos() {}

    @Schema(name = "IngestRuleType")
    public enum RuleType {
        grabber,
        rss,
        website
    }

    @Schema(name = "IngestRuleStatus")
    public enum RuleStatus {
        draft,
        active,
        paused,
        auto_paused,
        archived
    }

    @Schema(name = "IngestPublishMode")
    public enum PublishMode {
        moderate,
        auto,
        draft
    }

    @Schema(name = "GrabberItemStatus")
    public enum ItemStatus {
        discovered,
        filtered_out,
        suppressed_dedup,
        pending_moderation,
        rejected,
        approved,
        scheduled,
        published,
        publish_failed,
        failed_validation,
        failed_ingest
    }

    @Schema(name = "GrabberConfig")
    public record GrabberConfig(
            List<String> sourceIds,
            String rssUrl,
            Integer scanIntervalMinutes,
            PublishMode publishMode,
            String scheduleMode,
            Integer publishDelayMinutes,
            Map<String, Object> filters,
            Map<String, Object> transform,
            Map<String, Object> attribution,
            Map<String, Object> limits,
            Map<String, Object> reliability,
            Boolean aiRewriteEnabled,
            Integer rssMaxItemsPerRun) {}

    @Schema(name = "GrabberRuleCreate")
    public record GrabberRuleCreate(
            @NotNull RuleType type,
            @NotBlank @Size(min = 1, max = 120) String name,
            @NotNull UUID targetChannelId,
            @Valid GrabberConfig config,
            @NotNull Boolean userResponsibilityAccepted,
            Boolean activate) {}

    @Schema(name = "GrabberRulePatch")
    public record GrabberRulePatch(
            @Size(min = 1, max = 120) String name,
            UUID targetChannelId,
            @Valid GrabberConfig config,
            @NotNull Integer version) {}

    @Schema(name = "GrabberRule")
    public record GrabberRule(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            RuleType type,
            String name,
            UUID targetChannelId,
            RuleStatus status,
            GrabberConfig config,
            int configVersion,
            Instant userResponsibilityAcceptedAt,
            UUID userResponsibilityAcceptedBy,
            Instant lastRunAt,
            Instant nextRunAt,
            int consecutiveFailures,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt,
            int version,
            long copiedTotal) {}

    @Schema(name = "GrabberRun")
    public record GrabberRun(
            UUID id,
            UUID ruleId,
            UUID workspaceId,
            String trigger,
            String status,
            Instant startedAt,
            Instant finishedAt,
            int sourcesScanned,
            int itemsFound,
            int itemsNew,
            int itemsDeduped,
            int itemsPublished,
            String errorCode,
            String errorMessage,
            String traceId) {}

    @Schema(name = "GrabberItem")
    public record GrabberItem(
            UUID id,
            UUID ruleId,
            UUID runId,
            UUID workspaceId,
            String sourceNetwork,
            String sourceId,
            String remotePostId,
            String sourcePermalink,
            String sourceName,
            String contentText,
            String contentHash,
            ItemStatus status,
            String skipReason,
            UUID postId,
            Instant detectedAt,
            Instant moderatedAt,
            UUID moderatedBy) {}

    @Schema(name = "GrabberItemApproveRequest")
    public record GrabberItemApproveRequest(Instant scheduledAt) {}

    @Schema(name = "GrabberItemRejectRequest")
    public record GrabberItemRejectRequest(@Size(max = 500) String reason) {}

    @Schema(name = "GrabberItemEditApproveRequest")
    public record GrabberItemEditApproveRequest(@NotBlank @Size(max = 20000) String contentText, Instant scheduledAt) {}

    @Schema(name = "GrabberBulkModerationRequest")
    public record GrabberBulkModerationRequest(
            @NotNull @Size(min = 1, max = 100) List<UUID> itemIds,
            @NotNull BulkAction action,
            Instant scheduledAt,
            String reason) {}

    @Schema(name = "GrabberBulkAction")
    public enum BulkAction {
        approve,
        reject
    }

    @Schema(name = "GrabberBulkModerationResponse")
    public record GrabberBulkModerationResponse(int succeeded, int failed, List<UUID> failedIds) {}

    @Schema(name = "IngestPreviewRequest")
    public record IngestPreviewRequest(@NotNull RuleType type, List<String> sourceIds, String rssUrl, Integer limit) {}

    @Schema(name = "IngestPreviewResponse")
    public record IngestPreviewResponse(List<GrabberItem> items) {}

    @Schema(name = "GrabberRulePage")
    public record GrabberRulePage(List<GrabberRule> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "GrabberRunPage")
    public record GrabberRunPage(List<GrabberRun> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "GrabberItemPage")
    public record GrabberItemPage(List<GrabberItem> items, String nextCursor, boolean hasMore) {}
}
