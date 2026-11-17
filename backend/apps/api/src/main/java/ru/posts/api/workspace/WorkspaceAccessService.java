package ru.posts.api.workspace;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.persistence.entity.ProjectEntity;
import ru.posts.persistence.entity.ProjectMemberEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.entity.WorkspaceMemberEntity;
import ru.posts.persistence.repository.ProjectMemberRepository;
import ru.posts.persistence.repository.ProjectRepository;
import ru.posts.persistence.repository.WorkspaceMemberRepository;
import ru.posts.persistence.repository.WorkspaceRepository;
import ru.posts.security.rbac.RbacEvaluationContext;
import ru.posts.security.rbac.RbacPermissionEvaluator;
import ru.posts.security.rbac.RbacRole;

@Service
@Profile("!openapi")
@Transactional(readOnly = true)
public class WorkspaceAccessService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final RbacPermissionEvaluator rbac;

    public WorkspaceAccessService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            RbacPermissionEvaluator rbac) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.rbac = rbac;
    }

    public WorkspaceEntity requireWorkspace(UUID workspaceId) {
        return workspaceRepository
                .findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(WorkspaceAccessService::notFound);
    }

    /** Membership or 404 (IDOR). */
    public WorkspaceMembership requireMember(UUID workspaceId, UUID userId) {
        requireWorkspace(workspaceId);
        WorkspaceMemberEntity member = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(WorkspaceAccessService::notFound);
        return toMembership(member);
    }

    public WorkspaceMembership requirePermission(UUID workspaceId, UUID userId, String action) {
        WorkspaceMembership membership = requireMember(workspaceId, userId);
        requireAction(membership, action, RbacEvaluationContext.workspaceOnly());
        return membership;
    }

    public WorkspaceMembership requirePermission(
            UUID workspaceId, UUID userId, String action, RbacEvaluationContext ctx) {
        WorkspaceMembership membership = requireMember(workspaceId, userId);
        requireAction(membership, action, ctx);
        return membership;
    }

    public void requireAction(WorkspaceMembership membership, String action, RbacEvaluationContext ctx) {
        RbacEvaluationContext effective = enrich(membership, ctx);
        if (!rbac.isAllowed(membership.role(), action, effective)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ProblemCodes.FORBIDDEN, "Insufficient permissions");
        }
    }

    /**
     * Ensures the member may view the project. Missing grant / foreign project → 404 (not 403).
     */
    public ProjectEntity requireVisibleProject(WorkspaceMembership membership, UUID projectId) {
        ProjectEntity project = projectRepository
                .findByIdAndWorkspaceIdAndDeletedAtIsNull(projectId, membership.workspaceId())
                .orElseThrow(WorkspaceAccessService::notFound);
        if (!membership.role().seesAllProjects()
                && !membership.assignedProjectIds().contains(projectId)) {
            throw notFound();
        }
        return project;
    }

    public Set<UUID> assignedProjectIds(UUID workspaceId, UUID userId) {
        List<ProjectEntity> projects = projectRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        if (projects.isEmpty()) {
            return Set.of();
        }
        List<UUID> projectIds = projects.stream().map(ProjectEntity::getId).toList();
        Set<UUID> assigned = new HashSet<>();
        for (ProjectMemberEntity pm : projectMemberRepository.findByProjectIdInAndUserId(projectIds, userId)) {
            assigned.add(pm.getProjectId());
        }
        return Set.copyOf(assigned);
    }

    public boolean isAllowed(WorkspaceMembership membership, String action, RbacEvaluationContext ctx) {
        return rbac.isAllowed(membership.role(), action, enrich(membership, ctx));
    }

    private RbacEvaluationContext enrich(WorkspaceMembership membership, RbacEvaluationContext ctx) {
        Set<UUID> assigned =
                ctx.assignedProjectIds() == null || ctx.assignedProjectIds().isEmpty()
                        ? membership.assignedProjectIds()
                        : ctx.assignedProjectIds();
        UUID actor = ctx.actorUserId() == null ? membership.userId() : ctx.actorUserId();
        return new RbacEvaluationContext(
                ctx.projectId(), assigned, ctx.resourceAuthorId(), actor, ctx.clientApprovalEnabled());
    }

    private WorkspaceMembership toMembership(WorkspaceMemberEntity member) {
        RbacRole role = RbacRole.from(member.getRole());
        Set<UUID> assigned =
                role.seesAllProjects() ? Set.of() : assignedProjectIds(member.getWorkspaceId(), member.getUserId());
        return new WorkspaceMembership(member.getWorkspaceId(), member.getUserId(), role, assigned);
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Resource not found");
    }
}
