package ru.posts.api.notification;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.notification.NotificationDtos;

public interface NotificationService {

    CursorPage.NotificationPage list(UUID userId, UUID workspaceId, Boolean unreadOnly, CursorPage.Params page);

    AuthDtos.MessageResponse markRead(
            UUID userId, UUID workspaceId, NotificationDtos.MarkNotificationsReadRequest request);

    NotificationDtos.NotificationSettings getSettings(UUID userId, UUID workspaceId);

    NotificationDtos.NotificationSettings updateSettings(
            UUID userId, UUID workspaceId, NotificationDtos.UpdateNotificationSettingsRequest request);

    NotificationDtos.TelegramLinkStartResponse startTelegramLink(UUID userId, UUID workspaceId);

    AuthDtos.MessageResponse unlinkTelegram(UUID userId, UUID workspaceId);

    AuthDtos.MessageResponse subscribeWebPush(
            UUID userId, UUID workspaceId, NotificationDtos.WebPushSubscribeRequest request);

    AuthDtos.MessageResponse unsubscribeWebPush(UUID userId, UUID workspaceId, String endpoint);

    NotificationDtos.VapidPublicKeyResponse vapidPublicKey(UUID userId);

    NotificationDtos.UnsubscribeResponse unsubscribeByToken(String token);

    /** Optional: mint a one-click unsubscribe token for settings / email templates. */
    String createUnsubscribeToken(UUID userId, String eventType);

    Map<String, Object> handleTelegramNotifyWebhook(JsonNode body, String secretHeader);
}
