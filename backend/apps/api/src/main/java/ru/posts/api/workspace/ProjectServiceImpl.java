package ru.posts.api.workspace;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.project.ProjectDtos;
import ru.posts.api.web.support.CursorPages;
import ru.posts.persistence.entity.ProjectEntity;
import ru.posts.persistence.entity.ProjectMemberEntity;
import ru.posts.persistence.entity.WorkspaceMemberEntity;
import ru.posts.persistence.repository.ProjectMemberRepository;
import ru.posts.persistence.repository.ProjectRepository;
import ru.posts.persistence.repository.WorkspaceMemberRepository;
import ru.posts.security.rbac.RbacEvaluationContext;

@Service
@Profile("!openapi")
@Transactional
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceAccessService access;
    private final AuditRecorder auditRecorder;

    public ProjectServiceImpl(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceAccessService access,
            AuditRecorder auditRecorder) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.access = access;
        this.auditRecorder = auditRecorder;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.ProjectPage list(UUID userId, UUID workspaceId, CursorPage.Params page) {
        WorkspaceMembership membership = access.requirePermission(workspaceId, userId, "projects.view");
        List<ProjectEntity> projects = projectRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId).stream()
                .sorted(Comparator.comparing(ProjectEntity::getId).reversed())
                .toList();
        if (!membership.role().seesAllProjects()) {
            Set<UUID> granted = membership.assignedProjectIds();
            projects =
                    projects.stream().filter(p -> granted.contains(p.getId())).toList();
        }
        CursorPages.Slice<ProjectEntity> slice =
                CursorPages.slice(projects, page.cursor(), page.limitOrDefault(), ProjectEntity::getId);
        return new CursorPage.ProjectPage(
                slice.items().stream().map(this::toDto).toList(), slice.nextCursor(), slice.hasMore());
    }

    @Override
    public ProjectDtos.Project create(
            UUID userId, UUID workspaceId, ProjectDtos.CreateProjectRequest request, ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "projects.manage");
        access.requireWorkspace(workspaceId);
        ProjectEntity project = new ProjectEntity();
        project.setWorkspaceId(workspaceId);
        project.setName(request.name().trim());
        project.setColor(request.color() == null || request.color().isBlank() ? "#3B82F6" : request.color());
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        project = projectRepository.save(project);
        auditRecorder.record(
                workspaceId,
                userId,
                "project.created",
                "project",
                project.getId(),
                Map.of("name", project.getName(), "color", project.getColor()),
                ctx.ip());
        return toDto(project);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDtos.Project get(UUID userId, UUID projectId) {
        ProjectEntity project =
                projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(WorkspaceAccessService::notFound);
        WorkspaceMembership membership = access.requireMember(project.getWorkspaceId(), userId);
        // Outside ProjectGrant → 404 (not 403), same as IDOR for foreign resources.
        access.requireVisibleProject(membership, projectId);
        access.requireAction(
                membership,
                "projects.view",
                RbacEvaluationContext.forProject(projectId, membership.assignedProjectIds()));
        return toDto(project);
    }

    @Override
    public ProjectDtos.Project update(
            UUID userId, UUID projectId, ProjectDtos.UpdateProjectRequest request, ClientRequestContext ctx) {
        ProjectEntity project =
                projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(WorkspaceAccessService::notFound);
        access.requirePermission(project.getWorkspaceId(), userId, "projects.manage");
        if (request.name() != null && !request.name().isBlank()) {
            project.setName(request.name().trim());
        }
        if (request.color() != null && !request.color().isBlank()) {
            project.setColor(request.color());
        }
        project.setUpdatedAt(Instant.now());
        project = projectRepository.save(project);
        auditRecorder.record(
                project.getWorkspaceId(),
                userId,
                "project.updated",
                "project",
                projectId,
                Map.of("name", project.getName(), "color", project.getColor()),
                ctx.ip());
        return toDto(project);
    }

    @Override
    public AuthDtos.MessageResponse delete(UUID userId, UUID projectId, ClientRequestContext ctx) {
        ProjectEntity project =
                projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(WorkspaceAccessService::notFound);
        access.requirePermission(project.getWorkspaceId(), userId, "projects.manage");
        project.setDeletedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        projectMemberRepository.deleteByProjectId(projectId);
        auditRecorder.record(
                project.getWorkspaceId(), userId, "project.deleted", "project", projectId, Map.of(), ctx.ip());
        return new AuthDtos.MessageResponse("project_deleted");
    }

    @Override
    public AuthDtos.MessageResponse setMembers(
            UUID userId, UUID projectId, ProjectDtos.ProjectMembersRequest request, ClientRequestContext ctx) {
        ProjectEntity project =
                projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(WorkspaceAccessService::notFound);
        access.requirePermission(project.getWorkspaceId(), userId, "members.update");
        List<UUID> userIds = request.userIds() == null ? List.of() : request.userIds();
        Set<UUID> desired = new HashSet<>(userIds);
        Map<UUID, WorkspaceMemberEntity> members =
                workspaceMemberRepository.findByWorkspaceId(project.getWorkspaceId()).stream()
                        .collect(Collectors.toMap(WorkspaceMemberEntity::getUserId, m -> m));
        for (UUID grantUserId : desired) {
            if (!members.containsKey(grantUserId)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        ProblemCodes.VALIDATION_FAILED,
                        "User is not a workspace member: " + grantUserId);
            }
        }
        projectMemberRepository.deleteByProjectId(projectId);
        for (UUID grantUserId : desired) {
            ProjectMemberEntity pm = new ProjectMemberEntity();
            pm.setProjectId(projectId);
            pm.setUserId(grantUserId);
            pm.setCreatedAt(Instant.now());
            projectMemberRepository.save(pm);
        }
        auditRecorder.record(
                project.getWorkspaceId(),
                userId,
                "project.members_set",
                "project",
                projectId,
                Map.of("userIds", desired.stream().map(UUID::toString).toList()),
                ctx.ip());
        return new AuthDtos.MessageResponse("project_members_updated");
    }

    private ProjectDtos.Project toDto(ProjectEntity project) {
        return new ProjectDtos.Project(
                project.getId(),
                project.getWorkspaceId(),
                project.getName(),
                project.getColor(),
                project.getCreatedAt());
    }
}
