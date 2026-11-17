package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Invitations", description = "Workspace invitations by email (US-2.3)")
@OpenApiResponses
public class InvitationController {

    @GetMapping("/workspaces/{workspaceId}/invitations")
    @Operation(operationId = "invitationsList", summary = "List pending invitations for a workspace")
    @ApiResponse(responseCode = "200", description = "Invitations page")
    public CursorPage.InvitationPage list(
            @PathVariable UUID workspaceId, @Valid @ParameterObject CursorPage.Params page) {
        throw ApiStub.notImplemented("invitationsList");
    }

    @PostMapping("/workspaces/{workspaceId}/invitations")
    @Operation(operationId = "invitationsCreate", summary = "Invite a user by email (TTL 7 days)")
    @ApiResponse(
            responseCode = "201",
            description = "Invitation created",
            content = @Content(schema = @Schema(implementation = WorkspaceDtos.Invitation.class)))
    public WorkspaceDtos.Invitation create(
            @PathVariable UUID workspaceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkspaceDtos.CreateInvitationRequest request) {
        throw ApiStub.notImplemented("invitationsCreate");
    }

    @DeleteMapping("/invitations/{invitationId}")
    @Operation(operationId = "invitationsCancel", summary = "Cancel a pending invitation")
    @ApiResponse(responseCode = "200", description = "Cancelled")
    public AuthDtos.MessageResponse cancel(@PathVariable UUID invitationId) {
        throw ApiStub.notImplemented("invitationsCancel");
    }

    @PostMapping("/invitations/accept")
    @Operation(operationId = "invitationsAccept", summary = "Accept an invitation using the email token")
    @ApiResponse(
            responseCode = "200",
            description = "Accepted",
            content = @Content(schema = @Schema(implementation = WorkspaceDtos.Workspace.class)))
    public WorkspaceDtos.Workspace accept(@Valid @RequestBody WorkspaceDtos.AcceptInvitationRequest request) {
        throw ApiStub.notImplemented("invitationsAccept");
    }

    @GetMapping("/invitations/preview")
    @SecurityRequirements
    @Operation(operationId = "invitationsPreview", summary = "Preview invitation metadata by token (unauthenticated)")
    @ApiResponse(responseCode = "200", description = "Invitation preview")
    public WorkspaceDtos.Invitation preview(@RequestParam String token) {
        throw ApiStub.notImplemented("invitationsPreview");
    }
}
