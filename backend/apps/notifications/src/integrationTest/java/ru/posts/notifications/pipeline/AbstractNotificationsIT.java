package ru.posts.notifications.pipeline;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
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
import ru.posts.notifications.NotificationsApplication;

/**
 * Shared Spring context for notification ITs so Rabbit consumers are not duplicated across
 * competing ApplicationContexts (same Testcontainers broker).
 */
@SpringBootTest(
        classes = NotificationsApplication.class,
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "posts.notifications.retry-poll-interval-ms=60000",
            "server.port=0"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AbstractNotificationsIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @ServiceConnection
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    static final GreenMail GREEN_MAIL = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));

    @RegisterExtension
    static final WireMockExtension TELEGRAM_API = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    static {
        POSTGRES.start();
        RABBIT.start();
        GREEN_MAIL.start();
    }

    @AfterAll
    static void stopMail() {
        GREEN_MAIL.stop();
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    protected DomainEventProcessor domainEventProcessor;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.publisher-confirm-type", () -> "correlated");
        registry.add("spring.rabbitmq.publisher-returns", () -> "true");
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> GREEN_MAIL.getSmtp().getPort());
        registry.add("spring.mail.username", () -> "");
        registry.add("spring.mail.password", () -> "");
        registry.add("posts.notifications.telegram.bot-token", () -> "test-token");
        registry.add("posts.notifications.telegram.api-base-url", TELEGRAM_API::baseUrl);
        registry.add("posts.notifications.web-push.vapid-public-key", () -> "");
        registry.add("posts.notifications.web-push.vapid-private-key", () -> "");
    }

    @BeforeEach
    void cleanNotificationTables() {
        jdbcTemplate.update("DELETE FROM notification_deliveries");
        jdbcTemplate.update("DELETE FROM notification_unsubscribe_tokens");
        jdbcTemplate.update("DELETE FROM notification_web_push_subscriptions");
        jdbcTemplate.update("DELETE FROM notification_telegram_links");
        jdbcTemplate.update("DELETE FROM notification_settings");
        jdbcTemplate.update("DELETE FROM notifications");
        GREEN_MAIL.purgeEmailFromAllMailboxes();
        TELEGRAM_API.resetAll();
    }

    protected SeededUser seedOwner() {
        UUID userId = UuidV7.generate();
        UUID workspaceId = UuidV7.generate();
        UUID planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);
        String email = "notif-" + userId + "@example.com";
        jdbcTemplate.update("INSERT INTO users (id, email, name) VALUES (?, ?, ?)", userId, email, "Notify User");
        jdbcTemplate.update(
                """
                INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status)
                VALUES (?, ?, ?, ?, 'trial')
                """,
                workspaceId,
                "Notify WS",
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

    protected void linkTelegram(UUID userId, long chatId) {
        jdbcTemplate.update(
                """
                INSERT INTO notification_telegram_links (user_id, chat_id, linked_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                userId,
                chatId,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
    }

    protected record SeededUser(UUID userId, UUID workspaceId, String email) {}

    @SuppressWarnings("unused")
    private static final Class<?>[] REDIS_EXCLUDES = {
        RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class
    };
}
