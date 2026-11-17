package ru.posts.api.workspace;

import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.project.ProjectDtos;

public interface ProjectService {

    CursorPage.ProjectPage list(UUID userId, UUID workspaceId, CursorPage.Params page);

    ProjectDtos.Project create(
            UUID userId, UUID workspaceId, ProjectDtos.CreateProjectRequest request, ClientRequestContext ctx);

    ProjectDtos.Project get(UUID userId, UUID projectId);

    ProjectDtos.Project update(
            UUID userId, UUID projectId, ProjectDtos.UpdateProjectRequest request, ClientRequestContext ctx);

    AuthDtos.MessageResponse delete(UUID userId, UUID projectId, ClientRequestContext ctx);

    AuthDtos.MessageResponse setMembers(
            UUID userId, UUID projectId, ProjectDtos.ProjectMembersRequest request, ClientRequestContext ctx);
}
