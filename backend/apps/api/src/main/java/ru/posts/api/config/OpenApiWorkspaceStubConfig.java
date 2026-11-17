package ru.posts.api.config;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.project.ProjectDtos;
import ru.posts.api.web.dto.workspace.WorkspaceDtos;
import ru.posts.api.workspace.InvitationService;
import ru.posts.api.workspace.ProjectService;
import ru.posts.api.workspace.WorkspaceService;

/** Stubs so openapi profile can boot controllers without a database. */
@Configuration
@Profile("openapi")
public class OpenApiWorkspaceStubConfig {

    @Bean
    WorkspaceService workspaceService() {
        return new WorkspaceService() {
            @Override
            public CursorPage.WorkspacePage list(UUID userId, CursorPage.Params page) {
                return new CursorPage.WorkspacePage(List.of(), null, false);
            }

            @Override
            public WorkspaceDtos.Workspace create(
                    UUID userId, WorkspaceDtos.CreateWorkspaceRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public WorkspaceDtos.Workspace get(UUID userId, UUID workspaceId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public WorkspaceDtos.Workspace update(
                    UUID userId,
                    UUID workspaceId,
                    WorkspaceDtos.UpdateWorkspaceRequest request,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse delete(UUID userId, UUID workspaceId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.WorkspaceMemberPage listMembers(UUID userId, UUID workspaceId, CursorPage.Params page) {
                return new CursorPage.WorkspaceMemberPage(List.of(), null, false);
            }

            @Override
            public WorkspaceDtos.WorkspaceMember updateMember(
                    UUID userId,
                    UUID workspaceId,
                    UUID memberUserId,
                    WorkspaceDtos.UpdateMemberRequest request,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse removeMember(
                    UUID userId, UUID workspaceId, UUID memberUserId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.AuditLogPage listAudit(
                    UUID userId, UUID workspaceId, UUID actorId, String action, String entity, CursorPage.Params page) {
                return new CursorPage.AuditLogPage(List.of(), null, false);
            }
        };
    }

    @Bean
    ProjectService projectService() {
        return new ProjectService() {
            @Override
            public CursorPage.ProjectPage list(UUID userId, UUID workspaceId, CursorPage.Params page) {
                return new CursorPage.ProjectPage(List.of(), null, false);
            }

            @Override
            public ProjectDtos.Project create(
                    UUID userId, UUID workspaceId, ProjectDtos.CreateProjectRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public ProjectDtos.Project get(UUID userId, UUID projectId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public ProjectDtos.Project update(
                    UUID userId, UUID projectId, ProjectDtos.UpdateProjectRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse delete(UUID userId, UUID projectId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse setMembers(
                    UUID userId, UUID projectId, ProjectDtos.ProjectMembersRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }
        };
    }

    @Bean
    InvitationService invitationService() {
        return new InvitationService() {
            @Override
            public CursorPage.InvitationPage list(UUID userId, UUID workspaceId, CursorPage.Params page) {
                return new CursorPage.InvitationPage(List.of(), null, false);
            }

            @Override
            public WorkspaceDtos.Invitation create(
                    UUID userId,
                    UUID workspaceId,
                    WorkspaceDtos.CreateInvitationRequest request,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse cancel(UUID userId, UUID invitationId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public WorkspaceDtos.Workspace accept(
                    UUID userId, WorkspaceDtos.AcceptInvitationRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public WorkspaceDtos.Invitation preview(String token) {
                throw new NotImplementedException("openapi");
            }
        };
    }
}
