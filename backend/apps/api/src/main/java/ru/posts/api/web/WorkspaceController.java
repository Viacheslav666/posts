package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.ClientRequestContexts;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.workspace.WorkspaceDtos;
import ru.posts.api.workspace.WorkspaceService;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(path = "/api/v1/workspaces", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Workspaces", description = "Workspaces and members")
@OpenApiResponses
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    @Operation(operationId = "workspacesList", summary = "List workspaces for the current user")
    @ApiResponse(responseCode = "200", description = "Workspace page")
    public CursorPage.WorkspacePage list(@Valid @ParameterObject CursorPage.Params page) {
        return workspaceService.list(SecurityAuthSupport.requirePrincipal().userId(), page);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "workspacesCreate", summary = "Create a workspace")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = WorkspaceDtos.Workspace.class)))
    public WorkspaceDtos.Workspace create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkspaceDtos.CreateWorkspaceRequest request,
            HttpServletRequest httpRequest) {
        return workspaceService.create(
                SecurityAuthSupport.requirePrincipal().userId(), request, ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/{workspaceId}")
    @Operation(operationId = "workspacesGet", summary = "Get workspace by id")
    @ApiResponse(
            responseCode = "200",
            description = "Workspace",
            content = @Content(schema = @Schema(implementation = WorkspaceDtos.Workspace.class)))
    public WorkspaceDtos.Workspace get(@PathVariable UUID workspaceId) {
        return workspaceService.get(SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
    }

    @PutMapping("/{workspaceId}")
    @Operation(operationId = "workspacesUpdate", summary = "Rename workspace")
    @ApiResponse(
            responseCode = "200",
            description = "Updated",
            content = @Content(schema = @Schema(implementation = WorkspaceDtos.Workspace.class)))
    public WorkspaceDtos.Workspace update(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody WorkspaceDtos.UpdateWorkspaceRequest request,
            HttpServletRequest httpRequest) {
        return workspaceService.update(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @DeleteMapping("/{workspaceId}")
    @Operation(operationId = "workspacesDelete", summary = "Delete workspace (Owner only)")
    @ApiResponse(responseCode = "200", description = "Deleted")
    public AuthDtos.MessageResponse delete(@PathVariable UUID workspaceId, HttpServletRequest httpRequest) {
        return workspaceService.delete(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/{workspaceId}/members")
    @Operation(operationId = "workspaceMembersList", summary = "List workspace members")
    @ApiResponse(responseCode = "200", description = "Members page")
    public CursorPage.WorkspaceMemberPage listMembers(
            @PathVariable UUID workspaceId, @Valid @ParameterObject CursorPage.Params page) {
        return workspaceService.listMembers(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, page);
    }

    @PutMapping("/{workspaceId}/members/{userId}")
    @Operation(operationId = "workspaceMembersUpdate", summary = "Update member role and project grants")
    @ApiResponse(responseCode = "200", description = "Updated member")
    public WorkspaceDtos.WorkspaceMember updateMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @Valid @RequestBody WorkspaceDtos.UpdateMemberRequest request,
            HttpServletRequest httpRequest) {
        return workspaceService.updateMember(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                userId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    @Operation(operationId = "workspaceMembersRemove", summary = "Remove a member from the workspace")
    @ApiResponse(responseCode = "200", description = "Removed")
    public AuthDtos.MessageResponse removeMember(
            @PathVariable UUID workspaceId, @PathVariable UUID userId, HttpServletRequest httpRequest) {
        return workspaceService.removeMember(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                userId,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/{workspaceId}/audit")
    @Operation(
            operationId = "workspacesListAudit",
            summary = "List workspace audit log entries (US-2.4)",
            description = "Owner/Admin only (`audit.view`). Foreign workspace → 404 (IDOR). CSV export is P1.")
    @ApiResponse(
            responseCode = "200",
            description = "Audit page",
            content = @Content(schema = @Schema(implementation = CursorPage.AuditLogPage.class)))
    public CursorPage.AuditLogPage listAudit(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity,
            @Valid @ParameterObject CursorPage.Params page) {
        return workspaceService.listAudit(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, actorId, action, entity, page);
    }
}
