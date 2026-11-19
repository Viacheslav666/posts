package ru.posts.api.qa14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.ingest.SocialInboundCommand;
import ru.posts.messaging.topology.IngestTopology;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.social.inbound.RecordingSocialEventHandler;
import ru.posts.social.inbound.SocialInboundProcessor;
import ru.posts.social.inbound.subscription.EventSubscriptionService;
import ru.posts.testsupport.emulator.SocialEmulatorProcess;

/**
 * TC-IN-restart: simulate ingest consumer stop mid-stream — resume without loss or duplicates.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("nightly")
class Qa14IngestRestartIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static SocialEmulatorProcess emulator;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        if (!rabbit.isRunning()) {
            rabbit.start();
        }
        if (emulator == null) {
            try {
                System.setProperty("posts.repo.root", findRepoRoot());
                emulator = SocialEmulatorProcess.start();
            } catch (Exception ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }
        registry.add("posts.channels.social-base-url", emulator::baseUrl);
        registry.add("posts.social-inbound.recording-handler", () -> "true");
        registry.add("posts.crypto.kek", () -> "integration-test-kek-material");
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration");
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    SocialInboundProcessor processor;

    @Autowired
    RecordingSocialEventHandler recordingHandler;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    EventSubscriptionService subscriptionService;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeAll
    void startEmulator() throws Exception {
        emulator.reset();
    }

    @AfterAll
    void stopEmulator() {
        if (emulator != null) {
            emulator.close();
        }
    }

    @BeforeEach
    void reset() {
        recordingHandler.clear();
        drainQueue("ingest.vk");
    }

    @Test
    @DisplayName("TC-IN-restart: pause SocialInboundProcessor mid-queue → resume → 5 events, 0 dupes")
    void pauseMidStreamThenResumeWithoutDupes() {
        UUID workspaceId = UuidV7.generate();
        UUID userId = UuidV7.generate();
        seedWorkspace(workspaceId, userId);
        ChannelEntity channel = vkChannel(workspaceId, "931001");
        subscriptionService.ensure(channel.getId(), "suggest", List.of("wall_post_new"));

        for (int i = 1; i <= 5; i++) {
            enqueueSyntheticVk(channel, "evt-r-" + i, 9300 + i, "msg-" + i);
        }

        drainPartial("ingest.vk", 2);
        assertThat(recordingHandler.size()).isEqualTo(2);
        assertThat(inboundRows(channel.getId())).isEqualTo(2);

        drainRest("ingest.vk");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(recordingHandler.size()).isEqualTo(5);
            assertThat(inboundRows(channel.getId())).isEqualTo(5);
        });
    }

    private void enqueueSyntheticVk(ChannelEntity channel, String eventId, int postId, String text) {
        String payload =
                """
                {"type":"wall_post_new","event_id":"%s","group_id":"%s","object":{"id":%d,"text":"%s"}}
                """
                        .formatted(eventId, channel.getExternalId(), postId, text);
        SocialInboundCommand cmd = new SocialInboundCommand(
                "vk",
                channel.getId().toString(),
                payload,
                Instant.now(),
                UuidV7.generate().toString(),
                null);
        rabbitTemplate.convertAndSend(IngestTopology.INGEST_EXCHANGE, "vk", cmd);
    }

    private void drainPartial(String queue, int max) {
        int done = 0;
        while (done < max) {
            SocialInboundCommand command = rabbitTemplate.receiveAndConvert(queue, INBOUND_CMD);
            if (command == null) {
                break;
            }
            processor.process(command);
            done++;
        }
    }

    private void drainRest(String queue) {
        SocialInboundCommand command;
        while ((command = rabbitTemplate.receiveAndConvert(queue, INBOUND_CMD)) != null) {
            processor.process(command);
        }
    }

    private void drainQueue(String queue) {
        while (rabbitTemplate.receiveAndConvert(queue, INBOUND_CMD) != null) {
            // discard
        }
    }

    private int inboundRows(UUID channelId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM social_inbound_events WHERE channel_id = ?", Integer.class, channelId);
    }

    private static final ParameterizedTypeReference<SocialInboundCommand> INBOUND_CMD =
            new ParameterizedTypeReference<>() {};

    private ChannelEntity vkChannel(UUID workspaceId, String externalId) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(workspaceId);
        channel.setNetwork("vk");
        channel.setExternalId(externalId);
        channel.setType("group");
        channel.setTitle("VK restart");
        channel.setStatus("active");
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        return channelRepository.save(channel);
    }

    private void seedWorkspace(UUID workspaceId, UUID userId) {
        UUID planId = jdbc.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);
        jdbc.update(
                "INSERT INTO users (id, email, name) VALUES (?, ?, ?)", userId, "ingest-r-" + userId + "@ex.com", "IR");
        jdbc.update(
                """
                INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status)
                VALUES (?, 'IR', ?, ?, 'trial')
                """,
                workspaceId,
                userId,
                planId);
    }

    private static String findRepoRoot() {
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            if (java.nio.file.Files.isDirectory(dir.resolve("tests/social-emulator"))
                    && java.nio.file.Files.isDirectory(dir.resolve("backend"))) {
                return dir.toString();
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        return java.nio.file.Path.of("../..").toAbsolutePath().normalize().toString();
    }
}
