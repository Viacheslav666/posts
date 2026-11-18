package ru.posts.notifications.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.notify.NotifyDomainEvent;

/** TC-NOTIF-01: publication.failed → in-app row + email (GreenMail). */
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

        processAndDeliver(event);

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

        MimeMessage[] messages = GREEN_MAIL.getReceivedMessages();
        assertThat(messages).isNotEmpty();
        MimeMessage message = Arrays.stream(messages)
                .filter(m -> {
                    try {
                        return Arrays.stream(m.getAllRecipients())
                                .anyMatch(r -> r.toString().contains(user.email()));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow();
        assertThat(message.getSubject()).contains("Ошибка публикации");
    }
}
