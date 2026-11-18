package ru.posts.notifications.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.notify.NotifyDeliveryCommand;
import ru.posts.messaging.notify.NotifyDomainEvent;
import ru.posts.messaging.topology.NotifyTopology;
import ru.posts.notifications.NotificationsApplication;

/**
 * End-to-end coverage of the real AMQP path: domain event → {@code notify.events} listener →
 * channel queues → email delivery. Uses a dedicated broker/DB/SMTP so it cannot race the
 * synchronous processor ITs (listeners disabled there).
 */
@SpringBootTest(
        classes = NotificationsApplication.class,
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "spring.rabbitmq.listener.simple.auto-startup=true",
            "posts.notifications.retry-poll-interval-ms=60000",
            "posts.notifications.it.mode=rabbit-e2e",
            "server.port=0"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RabbitNotifyPipelineIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts_notify_rabbit")
            .withUsername("posts")
            .withPassword("posts");

    @ServiceConnection
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    static final int SMTP_PORT = ServerSetup.PORT_SMTP + 3200;

    static final GreenMail GREEN_MAIL =
            new GreenMail(new ServerSetup(SMTP_PORT, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));

    static {
        POSTGRES.start();
        RABBIT.start();
        GREEN_MAIL.start();
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
        registry.add("spring.mail.username", () -> "");
        registry.add("spring.mail.password", () -> "");
        registry.add("posts.notifications.telegram.bot-token", () -> "rabbit-e2e-token");
        registry.add("posts.notifications.telegram.api-base-url", () -> "http://127.0.0.1:9");
        registry.add("posts.notifications.web-push.vapid-public-key", () -> "");
        registry.add("posts.notifications.web-push.vapid-private-key", () -> "");
    }

    @BeforeEach
    void clean() throws Exception {
        jdbcTemplate.update("DELETE FROM notification_deliveries");
        jdbcTemplate.update("DELETE FROM notification_unsubscribe_tokens");
        jdbcTemplate.update("DELETE FROM notification_web_push_subscriptions");
        jdbcTemplate.update("DELETE FROM notification_telegram_links");
        jdbcTemplate.update("DELETE FROM notification_settings");
        jdbcTemplate.update("DELETE FROM notifications");
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    @Test
    @DisplayName("Rabbit path delivers publication.failed exactly once including redelivery")
    void rabbitPathDeliversExactlyOnceIncludingRedelivery() throws Exception {
        SeededUser user = seedOwner();
        UUID publicationId = UuidV7.generate();
        NotifyDomainEvent event = new NotifyDomainEvent(
                40L,
                "publication",
                publicationId,
                "publication.failed",
                Map.of("workspaceId", user.workspaceId().toString(), "network", "tg", "result", "auth"),
                "trace-rabbit-e2e");

        // First delivery through real listeners
        rabbitTemplate.convertAndSend(
                NotifyTopology.NOTIFY_EXCHANGE, NotifyTopology.eventRoutingKey("publication.failed"), event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(notificationCount(user.userId())).isEqualTo(1);
            assertThat(deliveryStatus(user.userId(), "in_app")).isEqualTo("sent");
            assertThat(deliveryStatus(user.userId(), "email")).isEqualTo("sent");
            assertThat(deliveryStatus(user.userId(), "ws")).isIn("sent", "skipped");
            assertThat(emailsFor(user.email())).hasSize(1);
        });

        UUID emailDeliveryId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM notification_deliveries
                WHERE user_id = ? AND channel = 'email'
                """,
                UUID.class,
                user.userId());
        assertThat(emailDeliveryId).isNotNull();

        // Application-level redelivery: same domain event published again (at-least-once / outbox retry)
        rabbitTemplate.convertAndSend(
                NotifyTopology.NOTIFY_EXCHANGE, NotifyTopology.eventRoutingKey("publication.failed"), event);

        // Channel-level redelivery: same email delivery command republished to notify.email
        NotifyDeliveryCommand emailCommand = new NotifyDeliveryCommand(
                emailDeliveryId,
                jdbcTemplate.queryForObject(
                        "SELECT notification_id FROM notification_deliveries WHERE id = ?",
                        UUID.class,
                        emailDeliveryId),
                user.userId(),
                user.workspaceId(),
                "email",
                "publication.failed",
                DomainEventProcessor.buildDedupeKey("publication.failed", publicationId, user.userId()),
                Map.of(
                        "workspaceId",
                        user.workspaceId().toString(),
                        "network",
                        "tg",
                        "result",
                        "auth",
                        "notificationId",
                        jdbcTemplate
                                .queryForObject(
                                        "SELECT notification_id FROM notification_deliveries WHERE id = ?",
                                        UUID.class,
                                        emailDeliveryId)
                                .toString()),
                "trace-rabbit-e2e-redeliver");
        rabbitTemplate.convertAndSend(NotifyTopology.NOTIFY_EXCHANGE, NotifyTopology.EMAIL_RK, emailCommand);

        // Give listeners time to process both redeliveries, then prove counts stayed at one.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(notificationCount(user.userId()))
                    .as("domain event redelivery must not create a second in-app row")
                    .isEqualTo(1);
            assertThat(deliveryCount(user.userId(), "email"))
                    .as("channel redelivery must not create a second email delivery row")
                    .isEqualTo(1);
            assertThat(deliveryStatus(user.userId(), "email")).isEqualTo("sent");
            assertThat(emailsFor(user.email()))
                    .as("email channel redelivery must not send a second SMTP message")
                    .hasSize(1);
            MimeMessage message = emailsFor(user.email())[0];
            assertThat(message.getSubject()).contains("Ошибка публикации");
        });
    }

    private Integer notificationCount(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'publication.failed'",
                Integer.class,
                userId);
    }

    private Integer deliveryCount(UUID userId, String channel) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_deliveries WHERE user_id = ? AND channel = ?",
                Integer.class,
                userId,
                channel);
    }

    private String deliveryStatus(UUID userId, String channel) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status FROM notification_deliveries
                WHERE user_id = ? AND channel = ?
                """,
                String.class,
                userId,
                channel);
    }

    private MimeMessage[] emailsFor(String email) throws Exception {
        MimeMessage[] all = GREEN_MAIL.getReceivedMessages();
        return Arrays.stream(all)
                .filter(m -> {
                    try {
                        return Arrays.stream(m.getAllRecipients())
                                .anyMatch(r -> r.toString().contains(email));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toArray(MimeMessage[]::new);
    }

    private SeededUser seedOwner() {
        UUID userId = UuidV7.generate();
        UUID workspaceId = UuidV7.generate();
        UUID planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);
        String email = "rabbit-" + userId + "@example.com";
        jdbcTemplate.update("INSERT INTO users (id, email, name) VALUES (?, ?, ?)", userId, email, "Rabbit User");
        jdbcTemplate.update(
                """
                INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status)
                VALUES (?, ?, ?, ?, 'trial')
                """,
                workspaceId,
                "Rabbit WS",
                userId,
                planId);
        jdbcTemplate.update(
                """
                INSERT INTO workspace_members (workspace_id, user_id, role, created_at)
                VALUES (?, ?, 'owner', ?)
                """,
                workspaceId,
                userId,
                Timestamp.from(Instant.now()));
        return new SeededUser(userId, workspaceId, email);
    }

    private record SeededUser(UUID userId, UUID workspaceId, String email) {}
}
