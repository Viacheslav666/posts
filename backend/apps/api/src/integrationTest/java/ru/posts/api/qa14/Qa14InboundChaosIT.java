package ru.posts.api.qa14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.api.auth.mail.LoggingAuthMailSender;
import ru.posts.api.channel.WorkspaceTokenVault;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.ingest.SocialInboundCommand;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.social.api.SocialLiveApiGuard;
import ru.posts.social.inbound.RecordingSocialEventHandler;
import ru.posts.social.inbound.SocialInboundProcessor;
import ru.posts.social.inbound.subscription.EventSubscriptionService;
import ru.posts.testsupport.emulator.SocialEmulatorProcess;

/** QA-14: inbound chaos — RabbitMQ unavailable → 503, then drain without loss; VK callback ×5 dedup. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("nightly")
class Qa14InboundChaosIT {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");

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
        if (emulator == null) {
            try {
                System.setProperty("posts.repo.root", findRepoRoot());
                emulator = SocialEmulatorProcess.start();
            } catch (Exception ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }
        registry.add("posts.channels.social-base-url", emulator::baseUrl);
        registry.add("posts.social-inbound.social-base-url", emulator::baseUrl);
        registry.add("posts.social-inbound.public-base-url", () -> "http://127.0.0.1:8080");
        registry.add("posts.public-base-url", () -> "http://127.0.0.1:8080");
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
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoggingAuthMailSender mailSender;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    WorkspaceTokenVault tokenVault;

    @Autowired
    EventSubscriptionService subscriptionService;

    @Autowired
    SocialInboundProcessor processor;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RecordingSocialEventHandler recordingHandler;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeAll
    void startEmulator() throws Exception {
        SocialLiveApiGuard.assertNotProductionSocialUrl(emulator.baseUrl());
        emulator.reset();
    }

    @BeforeEach
    void reset() throws Exception {
        recordingHandler.clear();
        mailSender.clear();
        emulator.reset();
        drainQueue("ingest.vk");
        if (!rabbit.isRunning()) {
            rabbit.start();
            await().atMost(Duration.ofSeconds(30)).until(rabbit::isRunning);
        }
    }

    @Test
    @DisplayName("TC-IN-chaos-rabbit: stop RabbitMQ → hook 503, after restart ×1 handler delivery")
    void rabbitUnavailableThenRecover() throws Exception {
        Fixture fx = seedWorkspace();
        ChannelEntity channel = vkChannel(fx, "-920001");
        subscriptionService.ensure(channel.getId(), "suggest", List.of("wall_post_new"));
        var sub = subscriptionService.getRequired(channel.getId());
        String secret = tokenVault.unseal(fx.workspaceId(), sub.getSecretEnc());

        rabbit.stop();
        await().atMost(Duration.ofSeconds(10)).until(() -> !rabbit.isRunning());

        ObjectNode body = callbackBody("evt-chaos-1", 701, "chaos", secret);
        mockMvc.perform(post("/hooks/vk/" + channel.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isServiceUnavailable());

        rabbit.start();
        await().atMost(Duration.ofSeconds(45)).until(rabbit::isRunning);
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(60)).until(() -> true);

        mockMvc.perform(post("/hooks/vk/" + channel.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            drainAndProcess("ingest.vk");
            assertThat(recordingHandler.size()).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM social_inbound_events WHERE channel_id = ?",
                        Integer.class,
                        channel.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("TC-IN-dedup-callback: VK callback ×5 → one inbound row + one handler delivery")
    void vkCallbackFiveTimesDedup() throws Exception {
        Fixture fx = seedWorkspace();
        ChannelEntity channel = vkChannel(fx, "-920002");
        subscriptionService.ensure(channel.getId(), "suggest", List.of("wall_post_new"));
        var sub = subscriptionService.getRequired(channel.getId());
        String secret = tokenVault.unseal(fx.workspaceId(), sub.getSecretEnc());
        ObjectNode body = callbackBody("evt-five-1", 801, "dedup", secret);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/hooks/vk/" + channel.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(body)))
                    .andExpect(status().isOk());
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            drainAndProcess("ingest.vk");
            assertThat(recordingHandler.size()).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM social_inbound_events WHERE channel_id = ?",
                        Integer.class,
                        channel.getId()))
                .isEqualTo(1);
    }

    private ObjectNode callbackBody(String eventId, int postId, String text, String secret) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", "wall_post_new");
        body.put("event_id", eventId);
        body.put("group_id", 920002);
        body.put("secret", secret);
        ObjectNode object = body.putObject("object");
        object.put("id", postId);
        object.put("from_id", 1);
        object.put("owner_id", -920002);
        object.put("date", Instant.now().getEpochSecond());
        object.put("post_type", "post");
        object.put("text", text);
        return body;
    }

    private static final ParameterizedTypeReference<SocialInboundCommand> INBOUND_CMD =
            new ParameterizedTypeReference<>() {};

    private void drainAndProcess(String queue) {
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

    private ChannelEntity vkChannel(Fixture fx, String externalId) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(fx.workspaceId());
        channel.setNetwork("vk");
        channel.setExternalId(externalId);
        channel.setType("group");
        channel.setTitle("VK chaos");
        channel.setStatus("active");
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        return channel;
    }

    private Fixture seedWorkspace() throws Exception {
        String email = "qa14-chaos-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", password, "name", "QA14"))))
                .andExpect(status().isCreated());
        String verifyToken = mailSender.messages().stream()
                .filter(m -> m.to().equalsIgnoreCase(email))
                .reduce((a, b) -> b)
                .map(m -> {
                    Matcher matcher = TOKEN_IN_BODY.matcher(m.body() == null ? "" : m.body());
                    assertThat(matcher.find()).isTrue();
                    return matcher.group(1);
                })
                .orElseThrow();
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verifyToken))))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        UUID workspaceId = UUID.fromString(objectMapper
                .readTree(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/v1/workspaces")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + objectMapper
                                                        .readTree(login.getResponse()
                                                                .getContentAsString())
                                                        .path("accessToken")
                                                        .asText()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("items")
                .get(0)
                .path("id")
                .asText());
        return new Fixture(workspaceId);
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

    private record Fixture(UUID workspaceId) {}
}
