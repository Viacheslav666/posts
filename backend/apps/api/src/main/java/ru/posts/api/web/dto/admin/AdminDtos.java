package ru.posts.api.web.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.posts.api.web.dto.common.ApiEnums;

public final class AdminDtos {

    private AdminDtos() {}

    @Schema(name = "AdminUserSummary")
    public record UserSummary(
            UUID id,
            String email,
            String name,
            boolean emailVerified,
            boolean platformAdmin,
            boolean totpEnabled,
            Instant createdAt,
            int workspaceCount) {}

    @Schema(name = "AdminUserCard")
    public record UserCard(
            UUID id,
            String email,
            String name,
            boolean emailVerified,
            boolean platformAdmin,
            boolean totpEnabled,
            Instant createdAt,
            List<UserWorkspaceLink> workspaces) {}

    @Schema(name = "AdminUserWorkspaceLink")
    public record UserWorkspaceLink(
            UUID workspaceId, String workspaceName, ApiEnums.MemberRole role, ApiEnums.BillingStatus billingStatus) {}

    @Schema(name = "AdminWorkspaceSummary")
    public record WorkspaceSummary(
            UUID id,
            String name,
            UUID ownerUserId,
            String ownerEmail,
            String planCode,
            ApiEnums.BillingStatus billingStatus,
            Instant createdAt,
            long channelCount) {}

    @Schema(name = "AdminWorkspaceCard")
    public record WorkspaceCard(
            UUID id,
            String name,
            UUID ownerUserId,
            String ownerEmail,
            String planCode,
            Map<String, Object> planLimits,
            ApiEnums.BillingStatus billingStatus,
            Instant trialEndsAt,
            Instant currentPeriodEnd,
            BigDecimal balance,
            List<ChannelBrief> channels,
            List<PublicationErrorBrief> recentPublicationErrors,
            Instant createdAt) {}

    @Schema(name = "AdminChannelBrief")
    public record ChannelBrief(UUID id, String network, String title, String status, String username) {}

    @Schema(name = "AdminPublicationErrorBrief")
    public record PublicationErrorBrief(
            UUID publicationId,
            String network,
            String state,
            String errorCode,
            String errorMessage,
            Instant createdAt) {}

    @Schema(name = "AdminImpersonateRequest")
    public record ImpersonateRequest(
            @NotBlank @Size(min = 3, max = 500) String reason,
            @Schema(description = "Optional workspace context for audit") UUID workspaceId) {}

    @Schema(name = "AdminImpersonateResponse")
    public record ImpersonateResponse(
            String accessToken,
            long expiresIn,
            Instant expiresAt,
            UUID targetUserId,
            String targetEmail,
            String reason,
            @Schema(description = "Always true — FE shows «вы вошли как…» banner") boolean impersonating) {}

    @Schema(name = "AdminPlanUpsertRequest")
    public record PlanUpsertRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin("0") BigDecimal priceMonth,
            @NotNull @DecimalMin("0") BigDecimal priceYear,
            @NotNull Map<String, Object> limits) {}

    @Schema(name = "AdminPromoCreateRequest")
    public record PromoCreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Schema(allowableValues = {"percent", "fixed", "trial_days"}) String kind,
            @NotNull BigDecimal value,
            @NotNull Instant validFrom,
            @NotNull Instant validUntil,
            Integer maxRedemptions,
            boolean stackable,
            boolean oncePerWorkspace,
            boolean active) {}

    @Schema(name = "AdminPromoUpdateRequest")
    public record PromoUpdateRequest(
            Instant validFrom,
            Instant validUntil,
            Integer maxRedemptions,
            Boolean stackable,
            Boolean oncePerWorkspace,
            Boolean active) {}

    @Schema(name = "AdminPromoCode")
    public record PromoCode(
            UUID id,
            String code,
            String kind,
            BigDecimal value,
            Instant validFrom,
            Instant validUntil,
            Integer maxRedemptions,
            int redemptionCount,
            boolean stackable,
            boolean oncePerWorkspace,
            boolean active,
            Instant createdAt) {}

    @Schema(name = "AdminAdjustSubscriptionRequest")
    public record AdjustSubscriptionRequest(
            @NotBlank String planCode,
            ApiEnums.BillingStatus billingStatus,
            Instant periodEnd,
            Instant trialEndsAt,
            @NotBlank @Size(min = 3, max = 500) String reason) {}

    @Schema(name = "AdminAdjustBalanceRequest")
    public record AdjustBalanceRequest(
            @NotNull BigDecimal amount,
            @NotBlank @Size(min = 3, max = 500) String reason,
            @Schema(example = "admin_credit") String kind) {}

    @Schema(name = "AdminLedgerEntry")
    public record LedgerEntry(
            UUID id, UUID workspaceId, BigDecimal amount, String kind, String description, Instant createdAt) {}

    @Schema(name = "AdminQueueInfo")
    public record QueueInfo(String name, boolean dlq, long messageCount, long consumerCount) {}

    @Schema(name = "AdminQueueMessage")
    public record QueueMessage(
            String messageId,
            String payload,
            Map<String, Object> headers,
            Integer redeliveredCount,
            Instant enqueuedAt) {}

    @Schema(name = "AdminQueueMessageActionResponse")
    public record QueueMessageActionResponse(
            String queue, String messageId, String action, boolean applied, String detail) {}

    @Schema(name = "AdminFeatureFlag")
    public record FeatureFlag(
            UUID id, String key, String description, boolean enabledGlobal, Instant createdAt, Instant updatedAt) {}

    @Schema(name = "AdminFeatureFlagUpsertRequest")
    public record FeatureFlagUpsertRequest(
            @NotBlank @Size(max = 64) String key, @Size(max = 500) String description, boolean enabledGlobal) {}

    @Schema(name = "AdminWorkspaceFeatureFlagRequest")
    public record WorkspaceFeatureFlagRequest(@NotNull Boolean enabled) {}

    @Schema(name = "AdminWorkspaceFeatureFlag")
    public record WorkspaceFeatureFlag(UUID workspaceId, String flagKey, boolean enabled, Instant updatedAt) {}

    @Schema(name = "AdminAnnouncement")
    public record Announcement(
            UUID id,
            String title,
            String body,
            String severity,
            boolean active,
            Instant startsAt,
            Instant endsAt,
            UUID createdBy,
            Instant createdAt) {}

    @Schema(name = "AdminAnnouncementCreateRequest")
    public record AnnouncementCreateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String body,
            @NotBlank @Schema(allowableValues = {"info", "warning", "critical"}) String severity,
            boolean active,
            Instant startsAt,
            Instant endsAt) {}

    @Schema(name = "AdminAnnouncementUpdateRequest")
    public record AnnouncementUpdateRequest(
            @Size(max = 200) String title,
            @Size(max = 4000) String body,
            @Schema(allowableValues = {"info", "warning", "critical"}) String severity,
            Boolean active,
            Instant startsAt,
            Instant endsAt) {}

    @Schema(name = "AdminAuditEntry")
    public record AuditEntry(
            UUID id,
            UUID actorUserId,
            String actorEmail,
            String action,
            String targetType,
            UUID targetId,
            String reason,
            Map<String, Object> details,
            String ip,
            Instant createdAt) {}
}
