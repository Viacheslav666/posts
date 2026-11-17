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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.workspace.WorkspaceDtos;
import ru.posts.api.web.support.ApiStub;

@RestController
@Validated
@RequestMapping(path = "/api/v1/workspaces", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Workspaces", description = "Workspaces and members")
@OpenApiResponses
public class WorkspaceController {

    @GetMapping
    @Operation(operationId = "workspacesList", summary = "List workspaces for the current user")
    @ApiResponse(responseCode = "200", description = "Workspace page")
    public CursorPage.WorkspacePage list(@Valid @ParameterObject CursorPage.Params page) {
        throw ApiStub.notImplemented("workspacesList");
    }

    @PostMapping
    @Operation(operationId = "workspacesCreate", summary = "Create a workspace")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = WorkspaceDtos.Workspace.class)))
    public WorkspaceDtos.Workspace create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkspaceDtos.CreateWorkspaceRequest request) {
        throw ApiStub.notImplemented("workspacesCreate");
    }

    @GetMapping("/{workspaceId}")
    @Operation(operationId = "workspacesGet", summary = "Get workspace by id")
    @ApiResponse(
            responseCode = "200",
            description = "Workspace",
            content = @Content(schema = @Schema(implementation = WorkspaceDtos.Workspace.class)))
    public WorkspaceDtos.Workspace get(@PathVariable UUID workspaceId) {
        throw ApiStub.notImplemented("workspacesGet");
    }

    @PutMapping("/{workspaceId}")
    @Operation(operationId = "workspacesUpdate", summary = "Rename workspace")
    @ApiResponse(
            responseCode = "200",
            description = "Updated",
            content = @Content(schema = @Schema(implementation = WorkspaceDtos.Workspace.class)))
    public WorkspaceDtos.Workspace update(
            @PathVariable UUID workspaceId, @Valid @RequestBody WorkspaceDtos.UpdateWorkspaceRequest request) {
        throw ApiStub.notImplemented("workspacesUpdate");
    }

    @DeleteMapping("/{workspaceId}")
    @Operation(operationId = "workspacesDelete", summary = "Delete workspace (Owner only)")
    @ApiResponse(responseCode = "200", description = "Deleted")
    public AuthDtos.MessageResponse delete(@PathVariable UUID workspaceId) {
        throw ApiStub.notImplemented("workspacesDelete");
    }

    @GetMapping("/{workspaceId}/members")
    @Operation(operationId = "workspaceMembersList", summary = "List workspace members")
    @ApiResponse(responseCode = "200", description = "Members page")
    public CursorPage.WorkspaceMemberPage listMembers(
            @PathVariable UUID workspaceId, @Valid @ParameterObject CursorPage.Params page) {
        throw ApiStub.notImplemented("workspaceMembersList");
    }

    @PutMapping("/{workspaceId}/members/{userId}")
    @Operation(operationId = "workspaceMembersUpdate", summary = "Update member role and project grants")
    @ApiResponse(responseCode = "200", description = "Updated member")
    public WorkspaceDtos.WorkspaceMember updateMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @Valid @RequestBody WorkspaceDtos.UpdateMemberRequest request) {
        throw ApiStub.notImplemented("workspaceMembersUpdate");
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    @Operation(operationId = "workspaceMembersRemove", summary = "Remove a member from the workspace")
    @ApiResponse(responseCode = "200", description = "Removed")
    public AuthDtos.MessageResponse removeMember(@PathVariable UUID workspaceId, @PathVariable UUID userId) {
        throw ApiStub.notImplemented("workspaceMembersRemove");
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
        throw ApiStub.notImplemented("workspacesListAudit");
    }
}
