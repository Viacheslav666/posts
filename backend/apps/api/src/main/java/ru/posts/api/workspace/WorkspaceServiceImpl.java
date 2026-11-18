package ru.posts.api.workspace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.workspace.WorkspaceDtos;
import ru.posts.api.web.support.CursorPages;
import ru.posts.persistence.entity.AuditLogEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.ProjectEntity;
import ru.posts.persistence.entity.ProjectMemberEntity;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.entity.WorkspaceMemberEntity;
import ru.posts.persistence.repository.AuditLogRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.ProjectMemberRepository;
import ru.posts.persistence.repository.ProjectRepository;
import ru.posts.persistence.repository.UserRepository;
import ru.posts.persistence.repository.WorkspaceMemberRepository;
import ru.posts.persistence.repository.WorkspaceRepository;
import ru.posts.security.rbac.RbacRole;

@Service
@Profile("!openapi")
@Transactional
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final WorkspaceAccessService access;
    private final AuditRecorder auditRecorder;

    public WorkspaceServiceImpl(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            PlanRepository planRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            WorkspaceAccessService access,
            AuditRecorder auditRecorder) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.access = access;
        this.auditRecorder = auditRecorder;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.WorkspacePage list(UUID userId, CursorPage.Params page) {
        List<WorkspaceMemberEntity> memberships = workspaceMemberRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return new CursorPage.WorkspacePage(List.of(), null, false);
        }
        Map<UUID, WorkspaceEntity> byId = workspaceRepository
                .findByIdInAndDeletedAtIsNull(memberships.stream()
                        .map(WorkspaceMemberEntity::getWorkspaceId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(WorkspaceEntity::getId, Function.identity()));
        List<WorkspaceEntity> ordered = memberships.stream()
                .map(m -> byId.get(m.getWorkspaceId()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(WorkspaceEntity::getId).reversed())
                .toList();
        Map<UUID, String> planCodes = loadPlanCodes(ordered);
        CursorPages.Slice<WorkspaceEntity> slice =
                CursorPages.slice(ordered, page.cursor(), page.limitOrDefault(), WorkspaceEntity::getId);
        List<WorkspaceDtos.Workspace> items = slice.items().stream()
                .map(w -> toDto(w, planCodes.get(w.getPlanId())))
                .toList();
        return new CursorPage.WorkspacePage(items, slice.nextCursor(), slice.hasMore());
    }

    @Override
    public WorkspaceDtos.Workspace create(
            UUID userId, WorkspaceDtos.CreateWorkspaceRequest request, ClientRequestContext ctx) {
        PlanEntity freePlan =
                planRepository.findByCode("free").orElseThrow(() -> new IllegalStateException("free plan missing"));
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setName(request.name().trim());
        workspace.setOwnerUserId(userId);
        workspace.setPlanId(freePlan.getId());
        workspace.setBillingStatus("active");
        workspace.setBillingModel("balance");
        workspace.setTrialEndsAt(null);
        workspace.setCreatedAt(Instant.now());
        workspace.setUpdatedAt(Instant.now());
        workspace = workspaceRepository.save(workspace);

        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspace.getId());
        member.setUserId(userId);
        member.setRole(RbacRole.owner.name());
        member.setCreatedAt(Instant.now());
        workspaceMemberRepository.save(member);

        auditRecorder.record(
                workspace.getId(),
                userId,
                "workspace.created",
                "workspace",
                workspace.getId(),
                Map.of("name", workspace.getName()),
                ctx.ip());
        return toDto(workspace, freePlan.getCode());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceDtos.Workspace get(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, "workspace.view");
        WorkspaceEntity workspace = access.requireWorkspace(workspaceId);
        return toDto(workspace, planCode(workspace.getPlanId()));
    }

    @Override
    public WorkspaceDtos.Workspace update(
            UUID userId, UUID workspaceId, WorkspaceDtos.UpdateWorkspaceRequest request, ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "workspace.update");
        WorkspaceEntity workspace = access.requireWorkspace(workspaceId);
        String previous = workspace.getName();
        workspace.setName(request.name().trim());
        workspace.setUpdatedAt(Instant.now());
        workspace = workspaceRepository.save(workspace);
        auditRecorder.record(
                workspaceId,
                userId,
                "workspace.updated",
                "workspace",
                workspaceId,
                Map.of("name", Map.of("from", previous, "to", workspace.getName())),
                ctx.ip());
        return toDto(workspace, planCode(workspace.getPlanId()));
    }

    @Override
    public AuthDtos.MessageResponse delete(UUID userId, UUID workspaceId, ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "workspace.delete");
        WorkspaceEntity workspace = access.requireWorkspace(workspaceId);
        workspace.setDeletedAt(Instant.now());
        workspace.setUpdatedAt(Instant.now());
        workspaceRepository.save(workspace);
        auditRecorder.record(workspaceId, userId, "workspace.deleted", "workspace", workspaceId, Map.of(), ctx.ip());
        return new AuthDtos.MessageResponse("workspace_deleted");
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.WorkspaceMemberPage listMembers(UUID userId, UUID workspaceId, CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "members.view");
        List<WorkspaceMemberEntity> members = workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                .sorted(Comparator.comparing(WorkspaceMemberEntity::getCreatedAt)
                        .reversed()
                        .thenComparing(WorkspaceMemberEntity::getUserId))
                .toList();
        CursorPages.Slice<WorkspaceMemberEntity> slice =
                CursorPages.slice(members, page.cursor(), page.limitOrDefault(), WorkspaceMemberEntity::getUserId);
        Map<UUID, UserEntity> users =
                userRepository
                        .findAllById(slice.items().stream()
                                .map(WorkspaceMemberEntity::getUserId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        List<WorkspaceDtos.WorkspaceMember> items = new ArrayList<>();
        for (WorkspaceMemberEntity member : slice.items()) {
            items.add(toMemberDto(member, users.get(member.getUserId()), projectIdsFor(member)));
        }
        return new CursorPage.WorkspaceMemberPage(items, slice.nextCursor(), slice.hasMore());
    }

    @Override
    public WorkspaceDtos.WorkspaceMember updateMember(
            UUID userId,
            UUID workspaceId,
            UUID memberUserId,
            WorkspaceDtos.UpdateMemberRequest request,
            ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "members.update");
        WorkspaceEntity workspace = access.requireWorkspace(workspaceId);
        WorkspaceMemberEntity member = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, memberUserId)
                .orElseThrow(WorkspaceAccessService::notFound);

        if (RbacRole.owner.name().equals(member.getRole())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Cannot change the workspace owner via this endpoint");
        }
        if (request.role() == ApiEnums.MemberRole.owner) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ProblemCodes.VALIDATION_FAILED,
                    "Use ownership transfer to assign owner role");
        }

        String previousRole = member.getRole();
        member.setRole(request.role().name());
        workspaceMemberRepository.save(member);

        List<UUID> projectIds = request.projectIds() == null ? List.of() : request.projectIds();
        replaceProjectGrants(workspaceId, memberUserId, projectIds);

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("role", Map.of("from", previousRole, "to", member.getRole()));
        diff.put("projectIds", projectIds);
        auditRecorder.record(
                workspaceId, userId, "member.role_changed", "workspace_member", memberUserId, diff, ctx.ip());

        UserEntity user = userRepository.findById(memberUserId).orElseThrow(WorkspaceAccessService::notFound);
        // silence unused
        Objects.requireNonNull(workspace);
        return toMemberDto(member, user, projectIds);
    }

    @Override
    public AuthDtos.MessageResponse removeMember(
            UUID userId, UUID workspaceId, UUID memberUserId, ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "members.remove");
        WorkspaceMemberEntity member = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, memberUserId)
                .orElseThrow(WorkspaceAccessService::notFound);
        if (RbacRole.owner.name().equals(member.getRole())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Cannot remove the workspace owner");
        }
        if (userId.equals(memberUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Cannot remove yourself");
        }
        List<ProjectEntity> projects = projectRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        if (!projects.isEmpty()) {
            projectMemberRepository.deleteByUserIdAndProjectIdIn(
                    memberUserId, projects.stream().map(ProjectEntity::getId).toList());
        }
        workspaceMemberRepository.deleteByWorkspaceIdAndUserId(workspaceId, memberUserId);
        auditRecorder.record(
                workspaceId, userId, "member.removed", "workspace_member", memberUserId, Map.of(), ctx.ip());
        return new AuthDtos.MessageResponse("member_removed");
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.AuditLogPage listAudit(
            UUID userId, UUID workspaceId, UUID actorId, String action, String entity, CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "audit.view");
        List<AuditLogEntity> all = auditLogRepository.findFiltered(workspaceId, actorId, action, entity);
        CursorPages.Slice<AuditLogEntity> slice =
                CursorPages.slice(all, page.cursor(), page.limitOrDefault(), AuditLogEntity::getId);
        List<WorkspaceDtos.AuditLogEntry> items =
                slice.items().stream().map(this::toAuditDto).toList();
        return new CursorPage.AuditLogPage(items, slice.nextCursor(), slice.hasMore());
    }

    void replaceProjectGrants(UUID workspaceId, UUID memberUserId, List<UUID> projectIds) {
        List<ProjectEntity> workspaceProjects = projectRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        Set<UUID> validIds =
                workspaceProjects.stream().map(ProjectEntity::getId).collect(Collectors.toSet());
        Set<UUID> desired = new HashSet<>();
        for (UUID projectId : projectIds) {
            if (!validIds.contains(projectId)) {
                throw WorkspaceAccessService.notFound();
            }
            desired.add(projectId);
        }
        if (!workspaceProjects.isEmpty()) {
            projectMemberRepository.deleteByUserIdAndProjectIdIn(
                    memberUserId,
                    workspaceProjects.stream().map(ProjectEntity::getId).toList());
        }
        for (UUID projectId : desired) {
            ProjectMemberEntity pm = new ProjectMemberEntity();
            pm.setProjectId(projectId);
            pm.setUserId(memberUserId);
            pm.setCreatedAt(Instant.now());
            projectMemberRepository.save(pm);
        }
    }

    private List<UUID> projectIdsFor(WorkspaceMemberEntity member) {
        if (RbacRole.owner.name().equals(member.getRole())
                || RbacRole.admin.name().equals(member.getRole())) {
            return projectRepository.findByWorkspaceIdAndDeletedAtIsNull(member.getWorkspaceId()).stream()
                    .map(ProjectEntity::getId)
                    .toList();
        }
        return new ArrayList<>(access.assignedProjectIds(member.getWorkspaceId(), member.getUserId()));
    }

    private Map<UUID, String> loadPlanCodes(List<WorkspaceEntity> workspaces) {
        Set<UUID> planIds = workspaces.stream()
                .map(WorkspaceEntity::getPlanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (planIds.isEmpty()) {
            return Map.of();
        }
        return planRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(PlanEntity::getId, PlanEntity::getCode));
    }

    private String planCode(UUID planId) {
        if (planId == null) {
            return "free";
        }
        return planRepository.findById(planId).map(PlanEntity::getCode).orElse("free");
    }

    private WorkspaceDtos.Workspace toDto(WorkspaceEntity workspace, String planCode) {
        return new WorkspaceDtos.Workspace(
                workspace.getId(),
                workspace.getName(),
                workspace.getOwnerUserId(),
                planCode == null ? "free" : planCode,
                ApiEnums.BillingStatus.valueOf(workspace.getBillingStatus()),
                workspace.getTrialEndsAt(),
                workspace.getCurrentPeriodEnd(),
                workspace.getCreatedAt());
    }

    private WorkspaceDtos.WorkspaceMember toMemberDto(
            WorkspaceMemberEntity member, UserEntity user, List<UUID> projectIds) {
        return new WorkspaceDtos.WorkspaceMember(
                member.getUserId(),
                user == null ? "" : user.getEmail(),
                user == null ? "" : user.getName(),
                ApiEnums.MemberRole.valueOf(member.getRole()),
                projectIds,
                member.getCreatedAt());
    }

    private WorkspaceDtos.AuditLogEntry toAuditDto(AuditLogEntity entry) {
        return new WorkspaceDtos.AuditLogEntry(
                entry.getId(),
                entry.getWorkspaceId(),
                entry.getActorId(),
                entry.getAction(),
                entry.getEntity(),
                entry.getEntityId(),
                entry.getDiff(),
                entry.getIp(),
                entry.getCreatedAt());
    }
}
