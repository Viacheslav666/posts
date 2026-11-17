package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.AuthService;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.user.UserDtos;
import ru.posts.security.auth.PostsPrincipal;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Me", description = "Current user profile and sessions")
@OpenApiResponses
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    @Operation(operationId = "meGet", summary = "Get current user profile")
    @ApiResponse(
            responseCode = "200",
            description = "Current user",
            content = @Content(schema = @Schema(implementation = UserDtos.UserProfile.class)))
    public UserDtos.UserProfile me() {
        return authService.me(SecurityAuthSupport.requirePrincipal().userId());
    }

    @PatchMapping("/me")
    @Operation(operationId = "meUpdate", summary = "Update profile fields (name, timezone, locale, avatar)")
    @ApiResponse(
            responseCode = "200",
            description = "Updated",
            content = @Content(schema = @Schema(implementation = UserDtos.UserProfile.class)))
    public UserDtos.UserProfile updateMe(@Valid @RequestBody UserDtos.UpdateMeRequest request) {
        return authService.updateMe(SecurityAuthSupport.requirePrincipal().userId(), request);
    }

    @PostMapping("/me/password")
    @Operation(operationId = "meChangePassword", summary = "Change password while authenticated")
    @ApiResponse(responseCode = "200", description = "Password changed")
    public AuthDtos.MessageResponse changePassword(@Valid @RequestBody UserDtos.UpdatePasswordRequest request) {
        PostsPrincipal principal = SecurityAuthSupport.requirePrincipal();
        return authService.changePassword(principal.userId(), principal.sessionId(), request);
    }

    @DeleteMapping("/me")
    @Operation(
            operationId = "meDelete",
            summary = "Request account deletion / anonymization (152-FZ)",
            description = "Schedules anonymization after a grace period.")
    @ApiResponse(responseCode = "202", description = "Deletion scheduled")
    public ResponseEntity<AuthDtos.MessageResponse> deleteMe() {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(authService.deleteMe(
                        SecurityAuthSupport.requirePrincipal().userId()));
    }

    @GetMapping("/me/sessions")
    @Operation(operationId = "meListSessions", summary = "List active and recent sessions (US-1.5)")
    @ApiResponse(responseCode = "200", description = "Sessions page")
    public CursorPage.SessionPage listSessions(@Valid @ParameterObject CursorPage.Params page) {
        PostsPrincipal principal = SecurityAuthSupport.requirePrincipal();
        return authService.listSessions(principal.userId(), principal.sessionId(), page);
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    @Operation(operationId = "meRevokeSession", summary = "Revoke a specific session")
    @ApiResponse(responseCode = "200", description = "Session revoked")
    public AuthDtos.MessageResponse revokeSession(@PathVariable UUID sessionId) {
        return authService.revokeSession(SecurityAuthSupport.requirePrincipal().userId(), sessionId);
    }

    @DeleteMapping("/me/sessions")
    @Operation(operationId = "meRevokeOtherSessions", summary = "Revoke all sessions except the current one")
    @ApiResponse(responseCode = "200", description = "Other sessions revoked")
    public AuthDtos.MessageResponse revokeOtherSessions() {
        PostsPrincipal principal = SecurityAuthSupport.requirePrincipal();
        return authService.revokeOtherSessions(principal.userId(), principal.sessionId());
    }
}
