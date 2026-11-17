package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.project.ProjectDtos;
import ru.posts.api.web.support.ApiStub;

@RestController
@Validated
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Projects", description = "Projects (clients/brands) inside a workspace")
@OpenApiResponses
public class ProjectController {

    @GetMapping("/workspaces/{workspaceId}/projects")
    @Operation(operationId = "projectsList", summary = "List projects visible to the current member")
    @ApiResponse(responseCode = "200", description = "Projects page")
    public CursorPage.ProjectPage list(@PathVariable UUID workspaceId, @Valid @ParameterObject CursorPage.Params page) {
        throw ApiStub.notImplemented("projectsList");
    }

    @PostMapping("/workspaces/{workspaceId}/projects")
    @Operation(operationId = "projectsCreate", summary = "Create a project")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = ProjectDtos.Project.class)))
    public ProjectDtos.Project create(
            @PathVariable UUID workspaceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProjectDtos.CreateProjectRequest request) {
        throw ApiStub.notImplemented("projectsCreate");
    }

    @GetMapping("/projects/{projectId}")
    @Operation(operationId = "projectsGet", summary = "Get project by id")
    @ApiResponse(
            responseCode = "200",
            description = "Project",
            content = @Content(schema = @Schema(implementation = ProjectDtos.Project.class)))
    public ProjectDtos.Project get(@PathVariable UUID projectId) {
        throw ApiStub.notImplemented("projectsGet");
    }

    @PutMapping("/projects/{projectId}")
    @Operation(operationId = "projectsUpdate", summary = "Update project name/color")
    @ApiResponse(responseCode = "200", description = "Updated")
    public ProjectDtos.Project update(
            @PathVariable UUID projectId, @Valid @RequestBody ProjectDtos.UpdateProjectRequest request) {
        throw ApiStub.notImplemented("projectsUpdate");
    }

    @DeleteMapping("/projects/{projectId}")
    @Operation(operationId = "projectsDelete", summary = "Delete a project")
    @ApiResponse(responseCode = "200", description = "Deleted")
    public AuthDtos.MessageResponse delete(@PathVariable UUID projectId) {
        throw ApiStub.notImplemented("projectsDelete");
    }

    @PutMapping("/projects/{projectId}/members")
    @Operation(operationId = "projectsSetMembers", summary = "Replace project membership grants")
    @ApiResponse(responseCode = "200", description = "Grants updated")
    public AuthDtos.MessageResponse setMembers(
            @PathVariable UUID projectId, @Valid @RequestBody ProjectDtos.ProjectMembersRequest request) {
        throw ApiStub.notImplemented("projectsSetMembers");
    }
}
