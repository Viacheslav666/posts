package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.user.UserDtos;
import ru.posts.api.web.support.ApiStub;
import ru.posts.api.web.support.LocalFixtures;

@RestController
@Validated
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Me", description = "Current user profile and sessions")
@OpenApiResponses
public class MeController {

    private final Environment environment;

    public MeController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/me")
    @Operation(
            operationId = "meGet",
            summary = "Get current user profile",
            description =
                    "In the `local` profile returns deterministic fixture data for FE/QA. Other profiles return 501 until BE-5.")
    @ApiResponse(
            responseCode = "200",
            description = "Current user",
            content =
                    @Content(
                            schema = @Schema(implementation = UserDtos.UserProfile.class),
                            examples =
                                    @ExampleObject(
                                            name = "local",
                                            value =
                                                    """
                                                    {
                                                      "id": "019a0000-0002-7000-8000-000000000001",
                                                      "email": "local@posts.test",
                                                      "emailVerified": true,
                                                      "name": "Local Dev",
                                                      "timezone": "Europe/Moscow",
                                                      "locale": "ru",
                                                      "totpEnabled": false,
                                                      "memberships": [
                                                        {
                                                          "workspaceId": "019a0000-0002-7000-8000-000000000002",
                                                          "workspaceName": "Личное пространство",
                                                          "role": "owner",
                                                          "billingStatus": "trial"
                                                        }
                                                      ]
                                                    }
                                                    """)))
    public UserDtos.UserProfile me() {
        if (environment.matchesProfiles("local")) {
            return LocalFixtures.localUser();
        }
        throw ApiStub.notImplemented("meGet");
    }

    @PatchMapping("/me")
    @Operation(operationId = "meUpdate", summary = "Update profile fields (name, timezone, locale, avatar)")
    @ApiResponse(
            responseCode = "200",
            description = "Updated",
            content = @Content(schema = @Schema(implementation = UserDtos.UserProfile.class)))
    public UserDtos.UserProfile updateMe(@Valid @RequestBody UserDtos.UpdateMeRequest request) {
        throw ApiStub.notImplemented("meUpdate");
    }

    @PostMapping("/me/password")
    @Operation(operationId = "meChangePassword", summary = "Change password while authenticated")
    @ApiResponse(responseCode = "200", description = "Password changed")
    public AuthDtos.MessageResponse changePassword(@Valid @RequestBody UserDtos.UpdatePasswordRequest request) {
        throw ApiStub.notImplemented("meChangePassword");
    }

    @DeleteMapping("/me")
    @Operation(
            operationId = "meDelete",
            summary = "Request account deletion / anonymization (152-FZ)",
            description = "Schedules anonymization after a grace period.")
    @ApiResponse(responseCode = "202", description = "Deletion scheduled")
    public AuthDtos.MessageResponse deleteMe() {
        throw ApiStub.notImplemented("meDelete");
    }

    @GetMapping("/me/sessions")
    @Operation(operationId = "meListSessions", summary = "List active and recent sessions (US-1.5)")
    @ApiResponse(responseCode = "200", description = "Sessions page")
    public CursorPage.Response<AuthDtos.SessionDto> listSessions(@Valid @ParameterObject CursorPage.Params page) {
        throw ApiStub.notImplemented("meListSessions");
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    @Operation(operationId = "meRevokeSession", summary = "Revoke a specific session")
    @ApiResponse(responseCode = "200", description = "Session revoked")
    public AuthDtos.MessageResponse revokeSession(@PathVariable UUID sessionId) {
        throw ApiStub.notImplemented("meRevokeSession");
    }

    @DeleteMapping("/me/sessions")
    @Operation(operationId = "meRevokeOtherSessions", summary = "Revoke all sessions except the current one")
    @ApiResponse(responseCode = "200", description = "Other sessions revoked")
    public AuthDtos.MessageResponse revokeOtherSessions() {
        throw ApiStub.notImplemented("meRevokeOtherSessions");
    }
}
