package ru.posts.notifications.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.notify.NotifyDomainEvent;
import ru.posts.messaging.topology.NotifyTopology;

class TelegramWireMockIT extends AbstractNotificationsIT {

    @RegisterExtension
    static WireMockExtension telegramApi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void telegramProps(DynamicPropertyRegistry registry) {
        registry.add("posts.notifications.telegram.bot-token", () -> "test-token");
        registry.add("posts.notifications.telegram.api-base-url", telegramApi::baseUrl);
        registry.add("posts.notifications.web-push.vapid-public-key", () -> "");
        registry.add("posts.notifications.web-push.vapid-private-key", () -> "");
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> "3025");
    }

    @Test
    @DisplayName("Telegram channel calls sendMessage via configurable base URL")
    void sendsTelegramMessage() {
        telegramApi.stubFor(post(urlPathMatching("/bottest-token/sendMessage"))
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

        rabbitTemplate.convertAndSend(
                NotifyTopology.NOTIFY_EXCHANGE, NotifyTopology.eventRoutingKey("publication.failed"), event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Integer sent = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM notification_deliveries
                    WHERE user_id = ? AND channel = 'telegram' AND status = 'sent'
                    """,
                    Integer.class,
                    user.userId());
            assertThat(sent).isEqualTo(1);
            telegramApi.verify(postRequestedFor(urlPathMatching("/bottest-token/sendMessage"))
                    .withRequestBody(containing("999001")));
        });
    }
}
