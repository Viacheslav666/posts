package ru.posts.notifications.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.notify.NotifyDomainEvent;
import ru.posts.messaging.topology.NotifyTopology;

class IdempotentDomainEventIT extends AbstractNotificationsIT {

    @Test
    @DisplayName("Double domain event yields a single in-app notification")
    void doubleEventIsIdempotent() {
        SeededUser user = seedOwner();
        UUID publicationId = UuidV7.generate();
        NotifyDomainEvent event = new NotifyDomainEvent(
                30L,
                "publication",
                publicationId,
                "publication.failed",
                Map.of("workspaceId", user.workspaceId().toString(), "network", "tg", "result", "auth"),
                "trace-idem");

        String rk = NotifyTopology.eventRoutingKey("publication.failed");
        rabbitTemplate.convertAndSend(NotifyTopology.NOTIFY_EXCHANGE, rk, event);
        rabbitTemplate.convertAndSend(NotifyTopology.NOTIFY_EXCHANGE, rk, event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Integer notifications = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'publication.failed'",
                    Integer.class,
                    user.userId());
            assertThat(notifications).isEqualTo(1);

            Integer deliveries = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM notification_deliveries WHERE user_id = ?", Integer.class, user.userId());
            // in_app + email + telegram + web_push + ws (tg/push may skip later but rows exist)
            assertThat(deliveries).isEqualTo(5);
        });
    }
}
