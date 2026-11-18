package ru.posts.api.web.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {}

    @Schema(name = "Notification")
    public record Notification(
            UUID id,
            @Schema(example = "publication.failed") String type,
            Map<String, Object> payload,
            Instant readAt,
            Instant createdAt,
            UUID workspaceId) {}

    @Schema(name = "ChannelPreference")
    public record ChannelPreference(boolean email, boolean telegram, boolean webPush, boolean inApp) {}

    @Schema(name = "QuietHours")
    public record QuietHours(
            boolean enabled,
            @Schema(description = "HH:mm local start", example = "22:00") String start,
            @Schema(description = "HH:mm local end", example = "08:00") String end,
            @Schema(example = "Europe/Moscow") String timezone) {}

    @Schema(name = "TelegramLinkStatus")
    public record TelegramLinkStatus(
            boolean linked, String botUsername, String deepLink, Instant linkedAt, String username) {}

    @Schema(name = "NotificationSettings")
    public record NotificationSettings(
            @Schema(description = "Per-event-type channel preferences") Map<String, ChannelPreference> preferences,
            QuietHours quietHours,
            @Schema(description = "Global email master switch (OpenAPI compat)") boolean emailEnabled,
            @Schema(description = "Global Telegram bot master switch (OpenAPI compat)") boolean telegramBotEnabled,
            @Schema(description = "Global Web Push master switch (OpenAPI compat)") boolean webPushEnabled,
            @Schema(description = "Global in-app master switch (OpenAPI compat)") boolean inAppEnabled,
            @Schema(description = "Mute categories", example = "[\"billing.reminder\"]") List<String> mutedTypes,
            TelegramLinkStatus telegram,
            String vapidPublicKey,
            int webPushSubscriptionCount) {}

    @Schema(name = "UpdateNotificationSettingsRequest")
    public record UpdateNotificationSettingsRequest(
            Map<String, @Valid ChannelPreference> preferences,
            @Valid QuietHours quietHours,
            Boolean emailEnabled,
            Boolean telegramBotEnabled,
            Boolean webPushEnabled,
            Boolean inAppEnabled,
            List<String> mutedTypes) {}

    @Schema(name = "MarkNotificationsReadRequest")
    public record MarkNotificationsReadRequest(
            @Schema(description = "Notification ids to mark read; omit or empty to mark all in workspace")
                    List<UUID> ids) {}

    @Schema(name = "WebPushSubscribeRequest")
    public record WebPushSubscribeRequest(
            @NotBlank @Size(max = 2048) String endpoint,
            @NotBlank @Size(max = 512) String p256dh,
            @NotBlank @Size(max = 512) String auth,
            @Size(max = 512) String userAgent) {}

    @Schema(name = "UnsubscribeResponse")
    public record UnsubscribeResponse(@Schema(example = "unsubscribed") String message) {}

    @Schema(name = "TelegramLinkStartResponse")
    public record TelegramLinkStartResponse(
            String deepLink, Instant codeExpiresAt, @Schema(example = "posts_notify_bot") String botUsername) {}

    @Schema(name = "VapidPublicKeyResponse")
    public record VapidPublicKeyResponse(String vapidPublicKey) {}
}
