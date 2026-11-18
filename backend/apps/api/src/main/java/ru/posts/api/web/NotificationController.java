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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.notification.NotificationService;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.notification.NotificationDtos;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Notifications", description = "In-app notifications and delivery settings (US-5.4)")
@OpenApiResponses
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    @Operation(operationId = "notificationsList", summary = "List notifications for the current user")
    @ApiResponse(responseCode = "200", description = "Notifications page")
    public CursorPage.NotificationPage list(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) Boolean unreadOnly,
            @Valid @ParameterObject CursorPage.Params page) {
        return notificationService.list(SecurityAuthSupport.requirePrincipal().userId(), workspaceId, unreadOnly, page);
    }

    @PutMapping("/notifications/read")
    @Operation(operationId = "notificationsMarkRead", summary = "Mark notifications as read")
    @ApiResponse(responseCode = "200", description = "Updated")
    public AuthDtos.MessageResponse markRead(
            @RequestParam UUID workspaceId, @Valid @RequestBody NotificationDtos.MarkNotificationsReadRequest request) {
        return notificationService.markRead(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, request);
    }

    @GetMapping("/notification-settings")
    @Operation(operationId = "notificationSettingsGet", summary = "Get notification delivery preferences")
    @ApiResponse(
            responseCode = "200",
            description = "Settings",
            content = @Content(schema = @Schema(implementation = NotificationDtos.NotificationSettings.class)))
    public NotificationDtos.NotificationSettings getSettings(@RequestParam UUID workspaceId) {
        return notificationService.getSettings(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
    }

    @PutMapping("/notification-settings")
    @Operation(operationId = "notificationSettingsUpdate", summary = "Update notification delivery preferences")
    @ApiResponse(responseCode = "200", description = "Updated settings")
    public NotificationDtos.NotificationSettings updateSettings(
            @RequestParam UUID workspaceId,
            @Valid @RequestBody NotificationDtos.UpdateNotificationSettingsRequest request) {
        return notificationService.updateSettings(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, request);
    }

    @PostMapping("/notification-settings/telegram/link")
    @Operation(
            operationId = "notificationTelegramLinkStart",
            summary = "Start Telegram notification-bot account linking")
    @ApiResponse(
            responseCode = "200",
            description = "Deep link to bot",
            content = @Content(schema = @Schema(implementation = NotificationDtos.TelegramLinkStartResponse.class)))
    public NotificationDtos.TelegramLinkStartResponse startTelegramLink(@RequestParam UUID workspaceId) {
        return notificationService.startTelegramLink(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
    }

    @DeleteMapping("/notification-settings/telegram")
    @Operation(operationId = "notificationTelegramUnlink", summary = "Unlink Telegram notification bot")
    @ApiResponse(responseCode = "200", description = "Unlinked")
    public AuthDtos.MessageResponse unlinkTelegram(@RequestParam UUID workspaceId) {
        return notificationService.unlinkTelegram(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
    }

    @PostMapping("/notification-settings/web-push")
    @Operation(operationId = "notificationWebPushSubscribe", summary = "Subscribe browser to Web Push")
    @ApiResponse(responseCode = "200", description = "Subscribed")
    public AuthDtos.MessageResponse subscribeWebPush(
            @RequestParam UUID workspaceId, @Valid @RequestBody NotificationDtos.WebPushSubscribeRequest request) {
        return notificationService.subscribeWebPush(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, request);
    }

    @DeleteMapping("/notification-settings/web-push")
    @Operation(operationId = "notificationWebPushUnsubscribe", summary = "Remove a Web Push subscription")
    @ApiResponse(responseCode = "200", description = "Unsubscribed")
    public AuthDtos.MessageResponse unsubscribeWebPush(@RequestParam UUID workspaceId, @RequestParam String endpoint) {
        return notificationService.unsubscribeWebPush(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, endpoint);
    }

    @GetMapping("/notifications/vapid-public-key")
    @Operation(operationId = "notificationVapidPublicKey", summary = "Get VAPID public key for Web Push")
    @ApiResponse(
            responseCode = "200",
            description = "VAPID public key",
            content = @Content(schema = @Schema(implementation = NotificationDtos.VapidPublicKeyResponse.class)))
    public NotificationDtos.VapidPublicKeyResponse vapidPublicKey() {
        return notificationService.vapidPublicKey(
                SecurityAuthSupport.requirePrincipal().userId());
    }

    @GetMapping("/notifications/unsubscribe")
    @Operation(operationId = "notificationUnsubscribe", summary = "One-click email unsubscribe by token")
    @ApiResponse(
            responseCode = "200",
            description = "Unsubscribed",
            content = @Content(schema = @Schema(implementation = NotificationDtos.UnsubscribeResponse.class)))
    public NotificationDtos.UnsubscribeResponse unsubscribe(@RequestParam String token) {
        return notificationService.unsubscribeByToken(token);
    }
}
