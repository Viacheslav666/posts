package ru.posts.notifications.pipeline;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.notify.NotifyDeliveryCommand;
import ru.posts.messaging.notify.NotifyDomainEvent;
import ru.posts.notifications.NotificationsApplication;
import ru.posts.notifications.channel.ChannelDeliveryHandler;
import ru.posts.persistence.entity.NotificationDeliveryEntity;
import ru.posts.persistence.repository.NotificationDeliveryRepository;

/**
 * Fast notification ITs: Rabbit listeners disabled; pipeline invoked synchronously via
 * {@link DomainEventProcessor} + {@link ChannelDeliveryHandler}. Broker E2E (listeners on,
 * redelivery) lives in {@link RabbitNotifyPipelineIT} with a dedicated broker/DB/SMTP.
 */
@SpringBootTest(
        classes = NotificationsApplication.class,
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "spring.rabbitmq.listener.simple.auto-startup=false",
            "posts.notifications.retry-poll-interval-ms=60000",
            "server.port=0"
        })
abstract class AbstractNotificationsIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @ServiceConnection
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    static final int SMTP_PORT = ServerSetup.PORT_SMTP + 3100;

    static final GreenMail GREEN_MAIL =
            new GreenMail(new ServerSetup(SMTP_PORT, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));

    /** Fixed port so a cached Spring context keeps a valid Telegram API base URL across IT classes. */
    static final int TELEGRAM_WIREMOCK_PORT = 18089;

    @RegisterExtension
    static final WireMockExtension TELEGRAM_API = WireMockExtension.newInstance()
            .options(wireMockConfig().port(TELEGRAM_WIREMOCK_PORT))
            .build();

    static {
        POSTGRES.start();
        RABBIT.start();
        GREEN_MAIL.start();
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected DomainEventProcessor domainEventProcessor;

    @Autowired
    protected ChannelDeliveryHandler channelDeliveryHandler;

    @Autowired
    protected NotificationDeliveryRepository deliveryRepository;

    @Autowired
    protected ru.posts.notifications.config.NotificationsProperties notificationsProperties;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
        registry.add("spring.mail.username", () -> "");
        registry.add("spring.mail.password", () -> "");
        registry.add("posts.notifications.telegram.bot-token", () -> "test-token");
        registry.add("posts.notifications.telegram.api-base-url", () -> "http://127.0.0.1:" + TELEGRAM_WIREMOCK_PORT);
        registry.add("posts.notifications.web-push.vapid-public-key", () -> "");
        registry.add("posts.notifications.web-push.vapid-private-key", () -> "");
    }

    @BeforeEach
    void cleanNotificationTables() throws Exception {
        jdbcTemplate.update("DELETE FROM notification_deliveries");
        jdbcTemplate.update("DELETE FROM notification_unsubscribe_tokens");
        jdbcTemplate.update("DELETE FROM notification_web_push_subscriptions");
        jdbcTemplate.update("DELETE FROM notification_telegram_links");
        jdbcTemplate.update("DELETE FROM notification_settings");
        jdbcTemplate.update("DELETE FROM notifications");
        GREEN_MAIL.purgeEmailFromAllMailboxes();
        TELEGRAM_API.resetAll();
        // Cached Spring context may outlive a WireMock restart across IT classes.
        notificationsProperties.telegram().setApiBaseUrl(TELEGRAM_API.baseUrl());
        notificationsProperties.telegram().setBotToken("test-token");
    }

    /** Process domain event and drain pending channel deliveries synchronously. */
    protected void processAndDeliver(NotifyDomainEvent event) {
        boolean ok = domainEventProcessor.process(event);
        if (!ok) {
            throw new IllegalStateException("DomainEventProcessor rejected event " + event.eventType());
        }
        List<NotificationDeliveryEntity> pending = deliveryRepository.findAll().stream()
                .filter(d -> "pending".equals(d.getStatus()))
                .toList();
        for (NotificationDeliveryEntity delivery : pending) {
            channelDeliveryHandler.handle(toCommand(delivery, event));
        }
    }

    private static NotifyDeliveryCommand toCommand(NotificationDeliveryEntity delivery, NotifyDomainEvent event) {
        UUID workspaceId = null;
        Object raw = event.payload() == null ? null : event.payload().get("workspaceId");
        if (raw != null) {
            workspaceId = UUID.fromString(raw.toString());
        }
        return new NotifyDeliveryCommand(
                delivery.getId(),
                delivery.getNotificationId(),
                delivery.getUserId(),
                workspaceId,
                delivery.getChannel(),
                delivery.getEventType(),
                delivery.getDedupeKey(),
                delivery.getPayload(),
                event.traceId());
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
