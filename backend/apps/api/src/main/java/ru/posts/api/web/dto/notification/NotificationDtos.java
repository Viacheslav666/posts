package ru.posts.api.web.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
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
            Instant createdAt) {}

    @Schema(name = "NotificationSettings")
    public record NotificationSettings(
            boolean emailEnabled,
            boolean telegramBotEnabled,
            boolean webPushEnabled,
            boolean inAppEnabled,
            @Schema(description = "Mute categories", example = "[\"billing.reminder\"]")
                    java.util.List<String> mutedTypes) {}

    @Schema(name = "UpdateNotificationSettingsRequest")
    public record UpdateNotificationSettingsRequest(
            Boolean emailEnabled,
            Boolean telegramBotEnabled,
            Boolean webPushEnabled,
            Boolean inAppEnabled,
            java.util.List<String> mutedTypes) {}

    @Schema(name = "MarkNotificationsReadRequest")
    public record MarkNotificationsReadRequest(@NotNull java.util.List<UUID> ids) {}
}
