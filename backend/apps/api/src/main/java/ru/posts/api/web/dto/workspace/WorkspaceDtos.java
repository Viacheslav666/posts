package ru.posts.api.web.dto.workspace;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.posts.api.web.dto.common.ApiEnums;

public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    @Schema(name = "Workspace")
    public record Workspace(
            UUID id,
            @Schema(example = "Агентство Ромашка") String name,
            UUID ownerUserId,
            String planCode,
            ApiEnums.BillingStatus billingStatus,
            Instant trialEndsAt,
            Instant currentPeriodEnd,
            Instant createdAt) {}

    @Schema(name = "CreateWorkspaceRequest")
    public record CreateWorkspaceRequest(
            @NotBlank @Size(min = 1, max = 120) @Schema(example = "Новый клиентский workspace") String name) {}

    @Schema(name = "UpdateWorkspaceRequest")
    public record UpdateWorkspaceRequest(@NotBlank @Size(min = 1, max = 120) String name) {}

    @Schema(name = "WorkspaceMember")
    public record WorkspaceMember(
            UUID userId,
            @Schema(example = "editor@example.com") String email,
            @Schema(example = "Мария") String name,
            ApiEnums.MemberRole role,
            List<UUID> projectIds,
            Instant createdAt) {}

    @Schema(name = "UpdateMemberRequest")
    public record UpdateMemberRequest(
            @NotNull ApiEnums.MemberRole role, @Schema(description = "Assigned project IDs") List<UUID> projectIds) {}

    @Schema(name = "Invitation")
    public record Invitation(
            UUID id,
            UUID workspaceId,
            @Schema(example = "new@example.com") String email,
            ApiEnums.MemberRole role,
            List<UUID> projectIds,
            Instant expiresAt,
            Instant acceptedAt,
            Instant createdAt) {}

    @Schema(name = "CreateInvitationRequest")
    public record CreateInvitationRequest(
            @NotBlank @Email String email,
            @NotNull ApiEnums.MemberRole role,
            @Schema(description = "Projects the invitee will join") List<UUID> projectIds) {}

    @Schema(name = "AcceptInvitationRequest")
    public record AcceptInvitationRequest(@NotBlank String token) {}
}
