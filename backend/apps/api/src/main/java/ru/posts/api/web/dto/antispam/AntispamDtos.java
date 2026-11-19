package ru.posts.api.web.dto.antispam;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AntispamDtos {

    private AntispamDtos() {}

    @Schema(name = "AntispamRule")
    public record AntispamRule(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            UUID channelId,
            String name,
            String status,
            int priority,
            List<String> sections,
            Map<String, Object> config,
            int configVersion,
            boolean dryRun,
            int version,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "AntispamRuleCreate")
    public record AntispamRuleCreate(
            @NotNull UUID channelId,
            @NotBlank @Size(max = 200) String name,
            Integer priority,
            List<String> sections,
            Map<String, Object> config,
            Boolean dryRun) {}

    @Schema(name = "AntispamRulePatch")
    public record AntispamRulePatch(
            @Size(max = 200) String name,
            Integer priority,
            List<String> sections,
            Map<String, Object> config,
            Boolean dryRun,
            Integer version) {}

    @Schema(name = "AntispamRulePage")
    public record AntispamRulePage(List<AntispamRule> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "AntispamWordList")
    public record AntispamWordList(
            UUID id, UUID workspaceId, String name, List<String> words, String mode, int version, Instant updatedAt) {}

    @Schema(name = "AntispamWordListCreate")
    public record AntispamWordListCreate(
            @NotBlank @Size(max = 200) String name, @NotNull List<@NotBlank String> words, @NotBlank String mode) {}

    @Schema(name = "AntispamWordListPatch")
    public record AntispamWordListPatch(
            @Size(max = 200) String name, List<String> words, String mode, Integer version) {}

    @Schema(name = "AntispamWordListPage")
    public record AntispamWordListPage(List<AntispamWordList> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "AntispamLogEntry")
    public record AntispamLogEntry(
            UUID id,
            UUID workspaceId,
            UUID channelId,
            UUID ruleId,
            String section,
            Map<String, Object> remoteRef,
            String authorRef,
            String textSnapshot,
            String attachmentsSummary,
            List<String> reasons,
            String action,
            String state,
            boolean restorable,
            List<UUID> remoteActionIds,
            Instant createdAt,
            UUID restoredBy,
            Instant restoredAt) {}

    @Schema(name = "AntispamLogPage")
    public record AntispamLogPage(List<AntispamLogEntry> items, String nextCursor, boolean hasMore) {}

    @Schema(name = "AntispamRestoreRequest")
    public record AntispamRestoreRequest(Boolean unban) {}

    @Schema(name = "AntispamScanResponse")
    public record AntispamScanResponse(UUID jobId, String status, String message) {}

    @Schema(name = "AntispamRuleStats")
    public record AntispamRuleStats(UUID ruleId, long hits, Map<String, Long> byReason) {}
}
