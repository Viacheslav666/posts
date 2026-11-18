package ru.posts.notifications.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.notify.NotifyDomainEvent;
import ru.posts.messaging.topology.NotifyTopology;

/** TC-NOTIF-01: publication.failed → in-app row + email (GreenMail) within SLA window. */
class TcNotifPublicationFailedIT extends AbstractNotificationsIT {

    @Test
    @DisplayName("TC-NOTIF-01: publication.failed creates in-app + email")
    void publicationFailedCreatesInAppAndEmail() throws Exception {
        SeededUser user = seedOwner();
        UUID publicationId = UuidV7.generate();
        NotifyDomainEvent event = new NotifyDomainEvent(
                10L,
                "publication",
                publicationId,
                "publication.failed",
                Map.of("workspaceId", user.workspaceId().toString(), "network", "tg", "result", "auth"),
                "trace-notif-01");

        rabbitTemplate.convertAndSend(
                NotifyTopology.NOTIFY_EXCHANGE, NotifyTopology.eventRoutingKey("publication.failed"), event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Integer notifications = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'publication.failed'",
                    Integer.class,
                    user.userId());
            assertThat(notifications).isEqualTo(1);

            var emailRows = jdbcTemplate.queryForList(
                    """
                    SELECT status, last_error FROM notification_deliveries
                    WHERE user_id = ? AND channel = 'email'
                    """,
                    user.userId());
            assertThat(emailRows)
                    .as("email deliveries: %s", emailRows)
                    .hasSize(1)
                    .first()
                    .satisfies(row -> assertThat(row.get("status")).isEqualTo("sent"));
        });

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(GREEN_MAIL.getReceivedMessages())
                .isNotEmpty());
        MimeMessage message = GREEN_MAIL.getReceivedMessages()[0];
        assertThat(message.getAllRecipients()[0].toString()).contains(user.email());
        assertThat(message.getSubject()).contains("Ошибка публикации");
    }
}
