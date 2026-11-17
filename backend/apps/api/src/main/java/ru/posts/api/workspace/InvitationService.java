package ru.posts.api.workspace;

import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.workspace.WorkspaceDtos;

public interface InvitationService {

    CursorPage.InvitationPage list(UUID userId, UUID workspaceId, CursorPage.Params page);

    WorkspaceDtos.Invitation create(
            UUID userId, UUID workspaceId, WorkspaceDtos.CreateInvitationRequest request, ClientRequestContext ctx);

    AuthDtos.MessageResponse cancel(UUID userId, UUID invitationId, ClientRequestContext ctx);

    WorkspaceDtos.Workspace accept(
            UUID userId, WorkspaceDtos.AcceptInvitationRequest request, ClientRequestContext ctx);

    WorkspaceDtos.Invitation preview(String token);
}
