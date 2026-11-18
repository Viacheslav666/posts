package ru.posts.api.web.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.posts.api.web.dto.common.ApiEnums;

public final class UserDtos {

    private UserDtos() {}

    @Schema(name = "UserProfile", description = "Current authenticated user (`GET /me`)")
    public record UserProfile(
            UUID id,
            @Schema(example = "local@posts.test") String email,
            boolean emailVerified,
            @Schema(example = "Local Dev") String name,
            String avatarUrl,
            @Schema(example = "Europe/Moscow") String timezone,
            @Schema(example = "ru") String locale,
            boolean totpEnabled,
            Instant createdAt,
            List<MembershipSummary> memberships,
            @Schema(description = "Platform admin (E7 /admin); not a workspace role") boolean platformAdmin,
            @Schema(description = "Resolved feature flags for the current user (global defaults)")
                    Map<String, Boolean> features,
            @Schema(description = "Present when access token is an impersonation session")
                    ImpersonationBanner impersonation,
            @Schema(description = "Active platform announcements (banners)") List<AnnouncementBanner> announcements) {}

    @Schema(name = "MembershipSummary")
    public record MembershipSummary(
            UUID workspaceId,
            @Schema(example = "Личное пространство") String workspaceName,
            ApiEnums.MemberRole role,
            ApiEnums.BillingStatus billingStatus) {}

    @Schema(name = "ImpersonationBanner", description = "FE shows «вы вошли как…» when active=true")
    public record ImpersonationBanner(boolean active, UUID actorUserId, String reason, Instant expiresAt) {}

    @Schema(name = "AnnouncementBanner")
    public record AnnouncementBanner(UUID id, String title, String body, String severity, Instant endsAt) {}

    @Schema(name = "UpdateMeRequest")
    public record UpdateMeRequest(
            @Size(min = 1, max = 120) String name,
            @Schema(example = "Europe/Moscow") String timezone,
            @Schema(example = "ru") String locale,
            String avatarUrl) {}

    @Schema(name = "UpdatePasswordRequest")
    public record UpdatePasswordRequest(
            @NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 128) String newPassword) {}
}
