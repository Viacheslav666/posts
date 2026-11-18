package ru.posts.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.notification.NotificationService;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.notification.NotificationDtos;

/** Stubs so openapi profile can boot NotificationController without a database. */
@Configuration
@Profile("openapi")
public class OpenApiNotificationStubConfig {

    @Bean
    NotificationService notificationService() {
        return new NotificationService() {
            @Override
            public CursorPage.NotificationPage list(
                    UUID userId, UUID workspaceId, Boolean unreadOnly, CursorPage.Params page) {
                return new CursorPage.NotificationPage(List.of(), null, false);
            }

            @Override
            public AuthDtos.MessageResponse markRead(
                    UUID userId, UUID workspaceId, NotificationDtos.MarkNotificationsReadRequest request) {
                return new AuthDtos.MessageResponse("ok");
            }

            @Override
            public NotificationDtos.NotificationSettings getSettings(UUID userId, UUID workspaceId) {
                return new NotificationDtos.NotificationSettings(
                        Map.of(),
                        new NotificationDtos.QuietHours(false, null, null, "Europe/Moscow"),
                        true,
                        true,
                        true,
                        true,
                        List.of(),
                        new NotificationDtos.TelegramLinkStatus(false, "posts_notify_bot", null, null, null),
                        "",
                        0);
            }

            @Override
            public NotificationDtos.NotificationSettings updateSettings(
                    UUID userId, UUID workspaceId, NotificationDtos.UpdateNotificationSettingsRequest request) {
                return getSettings(userId, workspaceId);
            }

            @Override
            public NotificationDtos.TelegramLinkStartResponse startTelegramLink(UUID userId, UUID workspaceId) {
                return new NotificationDtos.TelegramLinkStartResponse(
                        "https://t.me/posts_notify_bot?start=openapi", Instant.now(), "posts_notify_bot");
            }

            @Override
            public AuthDtos.MessageResponse unlinkTelegram(UUID userId, UUID workspaceId) {
                return new AuthDtos.MessageResponse("telegram_unlinked");
            }

            @Override
            public AuthDtos.MessageResponse subscribeWebPush(
                    UUID userId, UUID workspaceId, NotificationDtos.WebPushSubscribeRequest request) {
                return new AuthDtos.MessageResponse("web_push_subscribed");
            }

            @Override
            public AuthDtos.MessageResponse unsubscribeWebPush(UUID userId, UUID workspaceId, String endpoint) {
                return new AuthDtos.MessageResponse("web_push_unsubscribed");
            }

            @Override
            public NotificationDtos.VapidPublicKeyResponse vapidPublicKey(UUID userId) {
                return new NotificationDtos.VapidPublicKeyResponse("");
            }

            @Override
            public NotificationDtos.UnsubscribeResponse unsubscribeByToken(String token) {
                return new NotificationDtos.UnsubscribeResponse("unsubscribed");
            }

            @Override
            public String createUnsubscribeToken(UUID userId, String eventType) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public Map<String, Object> handleTelegramNotifyWebhook(JsonNode body, String secretHeader) {
                return Map.of("ok", true);
            }
        };
    }
}
