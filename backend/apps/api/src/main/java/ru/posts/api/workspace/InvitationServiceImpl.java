package ru.posts.api.workspace;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.auth.AuthProperties;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.auth.mail.AuthMailSender;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.workspace.WorkspaceDtos;
import ru.posts.api.web.support.CursorPages;
import ru.posts.persistence.entity.InvitationEntity;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.ProjectMemberEntity;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.entity.WorkspaceMemberEntity;
import ru.posts.persistence.repository.InvitationRepository;
import ru.posts.persistence.repository.OutboxRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.ProjectMemberRepository;
import ru.posts.persistence.repository.ProjectRepository;
import ru.posts.persistence.repository.UserRepository;
import ru.posts.persistence.repository.WorkspaceMemberRepository;
import ru.posts.security.crypto.SecureTokens;
import ru.posts.security.crypto.TokenHasher;

@Service
@Profile("!openapi")
@Transactional
public class InvitationServiceImpl implements InvitationService {

    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final InvitationRepository invitationRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService access;
    private final AuditRecorder auditRecorder;
    private final AuthMailSender mailSender;
    private final AuthProperties authProperties;
    private final OutboxRepository outboxRepository;

    public InvitationServiceImpl(
            InvitationRepository invitationRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            PlanRepository planRepository,
            UserRepository userRepository,
            WorkspaceAccessService access,
            AuditRecorder auditRecorder,
            AuthMailSender mailSender,
            AuthProperties authProperties,
            OutboxRepository outboxRepository) {
        this.invitationRepository = invitationRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.auditRecorder = auditRecorder;
        this.mailSender = mailSender;
        this.authProperties = authProperties;
        this.outboxRepository = outboxRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.InvitationPage list(UUID userId, UUID workspaceId, CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "members.view");
        List<InvitationEntity> pending = invitationRepository.findPendingByWorkspaceId(workspaceId).stream()
                .sorted(Comparator.comparing(InvitationEntity::getId).reversed())
                .toList();
        CursorPages.Slice<InvitationEntity> slice =
                CursorPages.slice(pending, page.cursor(), page.limitOrDefault(), InvitationEntity::getId);
        return new CursorPage.InvitationPage(
                slice.items().stream().map(this::toDto).toList(), slice.nextCursor(), slice.hasMore());
    }

    @Override
    public WorkspaceDtos.Invitation create(
            UUID userId, UUID workspaceId, WorkspaceDtos.CreateInvitationRequest request, ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "members.invite");
        WorkspaceEntity workspace = access.requireWorkspace(workspaceId);
        if (request.role() == ApiEnums.MemberRole.owner) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Cannot invite as owner");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        userRepository
                .findByEmailIgnoreCase(email)
                .flatMap(u -> workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, u.getId()))
                .ifPresent(m -> {
                    throw new ApiException(
                            HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "User is already a workspace member");
                });

        ensureSeatForInvite(workspace);

        List<UUID> projectIds = request.projectIds() == null ? List.of() : request.projectIds();
        validateProjects(workspaceId, projectIds);

        String rawToken = SecureTokens.urlSafe(32);
        InvitationEntity invitation = new InvitationEntity();
        invitation.setWorkspaceId(workspaceId);
        invitation.setEmail(email);
        invitation.setRole(request.role().name());
        invitation.setProjectIds(projectIds.toArray(UUID[]::new));
        invitation.setTokenHash(TokenHasher.sha256Hex(rawToken));
        invitation.setExpiresAt(Instant.now().plus(INVITE_TTL));
        invitation.setCreatedAt(Instant.now());
        invitation.setInvitedBy(userId);
        invitation = invitationRepository.save(invitation);

        String link = authProperties.getAppBaseUrl() + "/invite?token=" + rawToken;
        mailSender.send(
                email,
                "Приглашение в workspace Posts",
                "Вас пригласили в workspace «"
                        + workspace.getName()
                        + "» как "
                        + invitation.getRole()
                        + ".\nСсылка действует 7 дней:\n"
                        + link
                        + "\n\ntoken="
                        + rawToken);

        auditRecorder.record(
                workspaceId,
                userId,
                "invitation.created",
                "invitation",
                invitation.getId(),
                Map.of("email", email, "role", invitation.getRole()),
                ctx.ip());
        OutboxEntity outbox = new OutboxEntity();
        outbox.setAggregate("invitation");
        outbox.setAggregateId(invitation.getId());
        outbox.setEventType("invitation.created");
        outbox.setPayload(Map.of(
                "workspaceId", workspaceId.toString(),
                "email", email,
                "invitationId", invitation.getId().toString(),
                "role", invitation.getRole()));
        outbox.setCreatedAt(Instant.now());
        outboxRepository.save(outbox);
        return toDto(invitation);
    }

    @Override
    public AuthDtos.MessageResponse cancel(UUID userId, UUID invitationId, ClientRequestContext ctx) {
        InvitationEntity invitation =
                invitationRepository.findById(invitationId).orElseThrow(WorkspaceAccessService::notFound);
        access.requirePermission(invitation.getWorkspaceId(), userId, "members.invite");
        if (invitation.getAcceptedAt() != null || invitation.getCancelledAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Invitation is no longer pending");
        }
        invitation.setCancelledAt(Instant.now());
        invitationRepository.save(invitation);
        auditRecorder.record(
                invitation.getWorkspaceId(),
                userId,
                "invitation.cancelled",
                "invitation",
                invitationId,
                Map.of(),
                ctx.ip());
        return new AuthDtos.MessageResponse("invitation_cancelled");
    }

    @Override
    public WorkspaceDtos.Workspace accept(
            UUID userId, WorkspaceDtos.AcceptInvitationRequest request, ClientRequestContext ctx) {
        UserEntity user = userRepository.findById(userId).orElseThrow(WorkspaceAccessService::notFound);
        InvitationEntity invitation = invitationRepository
                .findByTokenHash(TokenHasher.sha256Hex(request.token()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.INVALID_TOKEN, "Invalid or expired invitation"));
        if (invitation.getCancelledAt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.INVALID_TOKEN, "Invitation was revoked");
        }
        if (invitation.getAcceptedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Invitation already accepted");
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.INVALID_TOKEN, "Invitation expired");
        }
        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, ProblemCodes.FORBIDDEN, "Invitation email does not match current user");
        }

        WorkspaceEntity workspace = access.requireWorkspace(invitation.getWorkspaceId());
        if (workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspace.getId(), userId)
                .isPresent()) {
            invitation.setAcceptedAt(Instant.now());
            invitationRepository.save(invitation);
            return toWorkspaceDto(workspace);
        }

        ensureSeatForAccept(workspace);

        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspace.getId());
        member.setUserId(userId);
        member.setRole(invitation.getRole());
        member.setInvitedBy(invitation.getInvitedBy());
        member.setCreatedAt(Instant.now());
        workspaceMemberRepository.save(member);

        UUID[] projectIds = invitation.getProjectIds() == null ? new UUID[0] : invitation.getProjectIds();
        for (UUID projectId : projectIds) {
            if (projectRepository
                    .findByIdAndWorkspaceIdAndDeletedAtIsNull(projectId, workspace.getId())
                    .isEmpty()) {
                continue;
            }
            ProjectMemberEntity pm = new ProjectMemberEntity();
            pm.setProjectId(projectId);
            pm.setUserId(userId);
            pm.setCreatedAt(Instant.now());
            projectMemberRepository.save(pm);
        }

        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);
        auditRecorder.record(
                workspace.getId(),
                userId,
                "invitation.accepted",
                "invitation",
                invitation.getId(),
                Map.of("role", invitation.getRole()),
                ctx.ip());
        return toWorkspaceDto(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceDtos.Invitation preview(String token) {
        InvitationEntity invitation = invitationRepository
                .findByTokenHash(TokenHasher.sha256Hex(token))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.INVALID_TOKEN, "Invalid or expired invitation"));
        if (invitation.getCancelledAt() != null
                || invitation.getAcceptedAt() != null
                || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.INVALID_TOKEN, "Invalid or expired invitation");
        }
        // Ensure workspace still exists (IDOR-safe: do not reveal cancelled/foreign details beyond invalid_token)
        access.requireWorkspace(invitation.getWorkspaceId());
        return toDto(invitation);
    }

    private void ensureSeatForInvite(WorkspaceEntity workspace) {
        long members = workspaceMemberRepository.countByWorkspaceId(workspace.getId());
        long pending = invitationRepository.countPendingByWorkspaceId(workspace.getId());
        int seats = resolveSeats(workspace);
        if (members + pending >= seats) {
            throw seatLimit(seats);
        }
    }

    private void ensureSeatForAccept(WorkspaceEntity workspace) {
        long members = workspaceMemberRepository.countByWorkspaceId(workspace.getId());
        int seats = resolveSeats(workspace);
        if (members >= seats) {
            throw seatLimit(seats);
        }
    }

    private static ApiException seatLimit(int seats) {
        return new ApiException(
                HttpStatus.PAYMENT_REQUIRED,
                ProblemCodes.PLAN_LIMIT_EXCEEDED,
                "Workspace seat limit reached",
                Map.of("limit", "seats", "seats", seats));
    }

    private int resolveSeats(WorkspaceEntity workspace) {
        if (workspace.getPlanId() == null) {
            return 1;
        }
        PlanEntity plan = planRepository.findById(workspace.getPlanId()).orElse(null);
        if (plan == null || plan.getLimits() == null) {
            return 1;
        }
        Object seats = plan.getLimits().get("seats");
        if (seats instanceof Number number) {
            return number.intValue();
        }
        return 1;
    }

    private void validateProjects(UUID workspaceId, List<UUID> projectIds) {
        for (UUID projectId : projectIds) {
            if (projectRepository
                    .findByIdAndWorkspaceIdAndDeletedAtIsNull(projectId, workspaceId)
                    .isEmpty()) {
                throw WorkspaceAccessService.notFound();
            }
        }
    }

    private WorkspaceDtos.Invitation toDto(InvitationEntity invitation) {
        List<UUID> projectIds =
                invitation.getProjectIds() == null ? List.of() : Arrays.asList(invitation.getProjectIds());
        return new WorkspaceDtos.Invitation(
                invitation.getId(),
                invitation.getWorkspaceId(),
                invitation.getEmail(),
                ApiEnums.MemberRole.valueOf(invitation.getRole()),
                projectIds,
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                invitation.getCreatedAt());
    }

    private WorkspaceDtos.Workspace toWorkspaceDto(WorkspaceEntity workspace) {
        String planCode = "free";
        if (workspace.getPlanId() != null) {
            planCode = planRepository
                    .findById(workspace.getPlanId())
                    .map(PlanEntity::getCode)
                    .orElse("free");
        }
        return new WorkspaceDtos.Workspace(
                workspace.getId(),
                workspace.getName(),
                workspace.getOwnerUserId(),
                planCode,
                ApiEnums.BillingStatus.valueOf(workspace.getBillingStatus()),
                workspace.getTrialEndsAt(),
                workspace.getCurrentPeriodEnd(),
                workspace.getCreatedAt());
    }
}
