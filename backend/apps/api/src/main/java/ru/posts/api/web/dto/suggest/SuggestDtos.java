package ru.posts.api.web.dto.suggest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** OpenAPI DTOs for BE-34 VK Suggests. */
public final class SuggestDtos {

    private SuggestDtos() {}

    @Schema(name = "SuggestSettingsStatus")
    public enum SettingsStatus {
        draft,
        active,
        paused,
        auto_paused
    }

    @Schema(name = "SuggestMode")
    public enum Mode {
        auto,
        manual,
        hybrid
    }

    @Schema(name = "SuggestItemStatus")
    public enum ItemStatus {
        filtered_out,
        pending_moderation,
        approved,
        scheduled,
        publishing,
        published,
        rejected,
        deleted_remote,
        removed_externally,
        failed
    }

    @Schema(name = "SuggestSettings")
    public record SuggestSettings(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            UUID channelId,
            SettingsStatus status,
            Mode mode,
            Map<String, Object> config,
            int configVersion,
            int version,
            Instant createdAt,
            Instant updatedAt,
            Instant lastSyncAt,
            Instant nextSyncAt) {}

    @Schema(name = "SuggestSettingsCreate")
    public record SuggestSettingsCreate(
            @NotNull UUID channelId,
            Mode mode,
            Map<String, Object> config) {}

    @Schema(name = "SuggestSettingsPatch")
    public record SuggestSettingsPatch(
            Mode mode,
            Map<String, Object> config,
            Integer version) {}

    @Schema(name = "SuggestSettingsCopyRequest")
    public record SuggestSettingsCopyRequest(@NotNull @Size(min = 1, max = 50) List<UUID> channelIds) {}

    @Schema(name = "SuggestSettingsCopyResult")
    public record SuggestSettingsCopyResult(List<SuggestSettingsCopyItem> results) {}

    @Schema(name = "SuggestSettingsCopyItem")
    public record SuggestSettingsCopyItem(UUID channelId, boolean ok, String error, UUID settingsId) {}

    @Schema(name = "SuggestSyncResult")
    public record SuggestSyncResult(int imported, int removedExternally, int markedPublished, String status) {}

    @Schema(name = "SuggestItem")
    public record SuggestItem(
            UUID id,
            UUID workspaceId,
            UUID channelId,
            UUID settingsId,
            String vkPostId,
            String authorVkId,
            String text,
            List<Map<String, Object>> attachments,
            String status,
            List<String> reasons,
            UUID decidedBy,
            Instant decidedAt,
            Instant scheduledFor,
            UUID remoteActionId,
            String publishedRemotePostId,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "SuggestItemApproveRequest")
    public record SuggestItemApproveRequest(
            String message, List<Map<String, Object>> attachments, Instant scheduledFor) {}

    @Schema(name = "SuggestItemRejectRequest")
    public record SuggestItemRejectRequest(Boolean deleteRemote) {}

    @Schema(name = "SuggestBulkAction")
    public enum BulkAction {
        approve_all,
        reject_all,
        approve_ids,
        reject_ids
    }

    @Schema(name = "SuggestBulkRequest")
    public record SuggestBulkRequest(
            @NotNull BulkAction action, List<UUID> ids, Boolean deleteRemote) {}

    @Schema(name = "SuggestBulkJob")
    public record SuggestBulkJob(
            UUID id,
            UUID workspaceId,
            UUID settingsId,
            UUID channelId,
            String action,
            String status,
            int total,
            int processed,
            int succeeded,
            int failed,
            int progressPercent,
            Instant createdAt,
            Instant updatedAt,
            Instant finishedAt) {}
}
