package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.notification.NotificationDtos;
import ru.posts.api.web.support.ApiStub;

@RestController
@Validated
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Notifications", description = "In-app notifications and delivery settings (US-5.4)")
@OpenApiResponses
public class NotificationController {

    @GetMapping("/notifications")
    @Operation(operationId = "notificationsList", summary = "List notifications for the current user")
    @ApiResponse(responseCode = "200", description = "Notifications page")
    public CursorPage.NotificationPage list(
            @RequestParam(required = false) Boolean unreadOnly, @Valid @ParameterObject CursorPage.Params page) {
        throw ApiStub.notImplemented("notificationsList");
    }

    @PutMapping("/notifications/read")
    @Operation(operationId = "notificationsMarkRead", summary = "Mark notifications as read")
    @ApiResponse(responseCode = "200", description = "Updated")
    public AuthDtos.MessageResponse markRead(
            @Valid @RequestBody NotificationDtos.MarkNotificationsReadRequest request) {
        throw ApiStub.notImplemented("notificationsMarkRead");
    }

    @GetMapping("/notification-settings")
    @Operation(operationId = "notificationSettingsGet", summary = "Get notification delivery preferences")
    @ApiResponse(
            responseCode = "200",
            description = "Settings",
            content = @Content(schema = @Schema(implementation = NotificationDtos.NotificationSettings.class)))
    public NotificationDtos.NotificationSettings getSettings() {
        throw ApiStub.notImplemented("notificationSettingsGet");
    }

    @PutMapping("/notification-settings")
    @Operation(operationId = "notificationSettingsUpdate", summary = "Update notification delivery preferences")
    @ApiResponse(responseCode = "200", description = "Updated settings")
    public NotificationDtos.NotificationSettings updateSettings(
            @Valid @RequestBody NotificationDtos.UpdateNotificationSettingsRequest request) {
        throw ApiStub.notImplemented("notificationSettingsUpdate");
    }
}
