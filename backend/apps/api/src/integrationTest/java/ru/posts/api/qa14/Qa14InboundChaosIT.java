package ru.posts.api.qa14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import ru.posts.persistence.entity.TelegramBotEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.TelegramBotRepository;
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
    TelegramBotRepository telegramBotRepository;

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
        ensureBrokerApp();
        drainQueue("ingest.vk");
        drainQueue("ingest.tg");
        drainQueue("ingest.max");
    }

    @Test
    @DisplayName("TC-IN-chaos-rabbit: stop_app → 503; queued events survive start_app; retry is not a second row")
    void rabbitUnavailableThenRecover() throws Exception {
        Fixture fx = seedWorkspace();
        ChannelEntity channel = vkChannel(fx, "920001");
        subscriptionService.ensure(channel.getId(), "suggest", List.of("wall_post_new"));
        var sub = subscriptionService.getRequired(channel.getId());
        String secret = tokenVault.unseal(fx.workspaceId(), sub.getSecretEnc());

        for (int i = 1; i <= 3; i++) {
            postVk(channel, secret, "evt-up-" + i, 700 + i, "up-" + i).andExpect(status().isOk());
        }

        rabbitmqctl("rabbitmqctl", "stop_app");
        ObjectNode dropped = callbackBody("evt-down-1", "920001", 799, "down", secret);
        mockMvc.perform(post("/hooks/vk/" + channel.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(dropped)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(content().string("unavailable"));

        ensureBrokerApp();
        await().atMost(Duration.ofSeconds(90)).untilAsserted(() -> postVk(channel, secret, "evt-down-1", 799, "down")
                .andExpect(status().isOk())
                .andExpect(content().string("ok")));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            drainAndProcess("ingest.vk");
            assertThat(recordingHandler.size()).isEqualTo(4);
        });
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM social_inbound_events WHERE channel_id = ?",
                        Integer.class,
                        channel.getId()))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("TC-IN-dedup-callback: VK callback ×5 → one inbound row + one handler delivery")
    void vkCallbackFiveTimesDedup() throws Exception {
        Fixture fx = seedWorkspace();
        ChannelEntity channel = vkChannel(fx, "920002");
        subscriptionService.ensure(channel.getId(), "suggest", List.of("wall_post_new"));
        var sub = subscriptionService.getRequired(channel.getId());
        String secret = tokenVault.unseal(fx.workspaceId(), sub.getSecretEnc());
        ObjectNode body = callbackBody("evt-five-1", "920002", 801, "dedup", secret);

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

    @Test
    @DisplayName("TC-IN-dedup-tg: same Telegram update_id twice → one inbound row")
    void telegramUpdateReplayDedup() throws Exception {
        Fixture fx = seedWorkspace();
        TelegramBotEntity bot =
                telegramBotRepository.findByStatusOrderByCreatedAtAsc("active").get(0);
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(fx.workspaceId());
        channel.setNetwork("tg");
        channel.setExternalId("-100920003");
        channel.setType("channel");
        channel.setTitle("TG replay");
        channel.setStatus("active");
        channel.setBotId(bot.getId());
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
        subscriptionService.ensure(channel.getId(), "suggest", List.of("channel_post"));

        ObjectNode update = objectMapper.createObjectNode();
        update.put("update_id", 920003);
        ObjectNode post = update.putObject("channel_post");
        post.put("message_id", 77);
        post.put("date", Instant.now().getEpochSecond());
        post.put("text", "replay");
        post.putObject("chat").put("id", -100920003L).put("type", "channel");
        byte[] payload = objectMapper.writeValueAsBytes(update);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/hooks/tg/" + bot.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Telegram-Bot-Api-Secret-Token", bot.getWebhookSecret())
                            .content(payload))
                    .andExpect(status().isOk());
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            drainAndProcess("ingest.tg");
            assertThat(recordingHandler.size()).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM social_inbound_events WHERE channel_id = ?",
                        Integer.class,
                        channel.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("TC-IN-dedup-max: same Max update ×10 → one inbound row")
    void maxUpdateTenTimesDedup() throws Exception {
        Fixture fx = seedWorkspace();
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(fx.workspaceId());
        channel.setNetwork("max");
        channel.setExternalId("max-920004");
        channel.setType("channel");
        channel.setTitle("Max replay");
        channel.setStatus("active");
        channel.setBotTokenEnc(tokenVault.seal(fx.workspaceId(), "emulator-max-token"));
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
        subscriptionService.ensure(channel.getId(), "suggest", List.of("comment_created"));
        var sub = subscriptionService.getRequired(channel.getId());
        String secret = tokenVault.unseal(fx.workspaceId(), sub.getSecretEnc());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("update_type", "comment_created");
        body.put("timestamp", 1_700_000_000_000L);
        body.putObject("comment")
                .put("id", "c-replay")
                .put("text", "again")
                .putObject("sender")
                .put("user_id", "u1");
        byte[] payload = objectMapper.writeValueAsBytes(body);
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/hooks/max/" + channel.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Max-Bot-Api-Secret", secret)
                            .content(payload))
                    .andExpect(status().isOk());
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            drainAndProcess("ingest.max");
            assertThat(recordingHandler.size()).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM social_inbound_events WHERE channel_id = ?",
                        Integer.class,
                        channel.getId()))
                .isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions postVk(
            ChannelEntity channel, String secret, String eventId, int postId, String text) throws Exception {
        ObjectNode body = callbackBody(eventId, channel.getExternalId(), postId, text, secret);
        return mockMvc.perform(post("/hooks/vk/" + channel.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private void ensureBrokerApp() throws Exception {
        rabbitmqctl("rabbitmqctl", "start_app");
    }

    private void rabbitmqctl(String... command) throws Exception {
        org.testcontainers.containers.Container.ExecResult result = rabbit.execInContainer(command);
        if (result.getExitCode() == 0) {
            return;
        }
        String text = result.getStdout() + "\n" + result.getStderr();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("already running") || lower.contains("already_running")) {
            return;
        }
        throw new IllegalStateException(String.join(" ", command) + " exited " + result.getExitCode() + ": " + text);
    }

    private ObjectNode callbackBody(String eventId, String groupId, int postId, String text, String secret) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", "wall_post_new");
        body.put("event_id", eventId);
        body.put("group_id", groupId);
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
        channel.setGroupTokenEnc(tokenVault.seal(fx.workspaceId(), "emulator-group-token"));
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        return channelRepository.save(channel);
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
