package ru.posts.notifications.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.notify.NotifyDomainEvent;
import ru.posts.messaging.topology.NotifyTopology;

/**
 * TC-NOTIF-01: publication.failed → in-app row + email (GreenMail) within SLA window.
 */
class TcNotifPublicationFailedIT extends AbstractNotificationsIT {

    private static final GreenMail GREEN_MAIL =
            new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));

    static {
        GREEN_MAIL.start();
    }

    @AfterAll
    static void stopMail() {
        GREEN_MAIL.stop();
    }

    @DynamicPropertySource
    static void mailProps(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> GREEN_MAIL.getSmtp().getPort());
        registry.add("spring.mail.username", () -> "");
        registry.add("spring.mail.password", () -> "");
        registry.add("posts.notifications.web-push.vapid-public-key", () -> "");
        registry.add("posts.notifications.web-push.vapid-private-key", () -> "");
    }

    @Test
    @DisplayName("TC-NOTIF-01: publication.failed creates in-app + email")
    void publicationFailedCreatesInAppAndEmail() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();
        SeededUser user = seedOwner();
        UUID publicationId = UuidV7.generate();
        NotifyDomainEvent event = new NotifyDomainEvent(
                10L,
                "publication",
                publicationId,
                "publication.failed",
                Map.of("workspaceId", user.workspaceId().toString(), "network", "tg", "result", "auth"),
                "trace-notif-01");

        rabbitTemplate.convertAndSend(NotifyTopology.NOTIFY_EXCHANGE, "publication.failed", event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Integer notifications = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'publication.failed'",
                    Integer.class,
                    user.userId());
            assertThat(notifications).isEqualTo(1);

            Integer emailDeliveries = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM notification_deliveries
                    WHERE user_id = ? AND channel = 'email' AND status = 'sent'
                    """,
                    Integer.class,
                    user.userId());
            assertThat(emailDeliveries).isEqualTo(1);
        });

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(GREEN_MAIL.getReceivedMessages())
                .isNotEmpty());
        MimeMessage message = GREEN_MAIL.getReceivedMessages()[0];
        assertThat(message.getAllRecipients()[0].toString()).contains(user.email());
        assertThat(message.getSubject()).contains("Ошибка публикации");
    }
}
