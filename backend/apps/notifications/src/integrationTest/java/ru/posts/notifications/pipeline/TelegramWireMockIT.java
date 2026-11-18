package ru.posts.notifications.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.notify.NotifyDomainEvent;

class TelegramWireMockIT extends AbstractNotificationsIT {

    @Test
    @DisplayName("Telegram channel calls sendMessage via configurable base URL")
    void sendsTelegramMessage() {
        assertThat(notificationsProperties.telegram().botToken()).isEqualTo("test-token");
        assertThat(notificationsProperties.telegram().apiBaseUrl()).isEqualTo(TELEGRAM_API.baseUrl());

        TELEGRAM_API.stubFor(post(urlPathMatching("/bottest-token/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":{\"message_id\":42}}")));

        SeededUser user = seedOwner();
        linkTelegram(user.userId(), 999001L);
        UUID publicationId = UuidV7.generate();

        NotifyDomainEvent event = new NotifyDomainEvent(
                20L,
                "publication",
                publicationId,
                "publication.failed",
                Map.of("workspaceId", user.workspaceId().toString(), "network", "tg", "result", "auth"),
                "trace-tg");

        processAndDeliver(event);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT channel, status, last_error FROM notification_deliveries
                WHERE user_id = ? AND channel = 'telegram'
                """,
                user.userId());
        assertThat(rows)
                .as(
                        "telegram delivery rows=%s apiBaseUrl=%s",
                        rows, notificationsProperties.telegram().apiBaseUrl())
                .hasSize(1)
                .first()
                .extracting(row -> row.get("status"))
                .isEqualTo("sent");
        TELEGRAM_API.verify(
                postRequestedFor(urlPathMatching("/bottest-token/sendMessage")).withRequestBody(containing("999001")));
    }
}
