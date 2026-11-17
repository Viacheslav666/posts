package ru.posts.api.workspace;

import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.workspace.WorkspaceDtos;

public interface WorkspaceService {

    CursorPage.WorkspacePage list(UUID userId, CursorPage.Params page);

    WorkspaceDtos.Workspace create(UUID userId, WorkspaceDtos.CreateWorkspaceRequest request, ClientRequestContext ctx);

    WorkspaceDtos.Workspace get(UUID userId, UUID workspaceId);

    WorkspaceDtos.Workspace update(
            UUID userId, UUID workspaceId, WorkspaceDtos.UpdateWorkspaceRequest request, ClientRequestContext ctx);

    AuthDtos.MessageResponse delete(UUID userId, UUID workspaceId, ClientRequestContext ctx);

    CursorPage.WorkspaceMemberPage listMembers(UUID userId, UUID workspaceId, CursorPage.Params page);

    WorkspaceDtos.WorkspaceMember updateMember(
            UUID userId,
            UUID workspaceId,
            UUID memberUserId,
            WorkspaceDtos.UpdateMemberRequest request,
            ClientRequestContext ctx);

    AuthDtos.MessageResponse removeMember(UUID userId, UUID workspaceId, UUID memberUserId, ClientRequestContext ctx);

    CursorPage.AuditLogPage listAudit(
            UUID userId, UUID workspaceId, UUID actorId, String action, String entity, CursorPage.Params page);
}
