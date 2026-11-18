package ru.posts.api.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
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
import ru.posts.social.inbound.RecordingSocialEventHandler;
import ru.posts.social.inbound.SocialInboundProcessor;
import ru.posts.social.inbound.album.TelegramAlbumAggregator;
import ru.posts.social.inbound.subscription.EventSubscriptionService;
import ru.posts.testsupport.emulator.SocialEmulatorProcess;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SocialInboundIT {

    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static SocialEmulatorProcess emulator;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        try {
            if (emulator == null) {
                System.setProperty("posts.repo.root", findRepoRoot());
                emulator = SocialEmulatorProcess.start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (emulator != null) {
                        emulator.close();
                    }
                }));
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
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
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
    RecordingSocialEventHandler recordingHandler;

    @Autowired
    SocialInboundProcessor processor;

    @Autowired
    TelegramAlbumAggregator albumAggregator;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @BeforeAll
    void ensureEmulator() throws Exception {
        if (emulator == null) {
            emulator = SocialEmulatorProcess.start();
        }
        emulator.reset();
    }

    @AfterAll
    void stopEmulator() {
        if (emulator != null) {
            emulator.close();
            emulator = null;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        mailSender.clear();
        recordingHandler.clear();
        emulator.reset();
        drainQueue("ingest.vk");
        drainQueue("ingest.tg");
        drainQueue("ingest.max");
    }

    @Test
    @DisplayName("VK ensure registers callback server; confirmation returns stored code; triple retry → 1 handler")
    void vkEnsureConfirmationAndDedup() throws Exception {
        Fixture fx = seedWorkspace();
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(fx.workspaceId());
        channel.setNetwork("vk");
        channel.setExternalId("777001");
        channel.setType("group");
        channel.setTitle("VK group");
        channel.setStatus("active");
        channel.setGroupTokenEnc(tokenVault.seal(fx.workspaceId(), "emulator-group-token"));
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);

        subscriptionService.ensure(channel.getId(), "suggest", List.of("wall_post_new", "wall_reply_new"));

        var sub = subscriptionService.getRequired(channel.getId());
        assertThat(sub.getConfirmationCode()).isNotBlank();
        assertThat(sub.getRemoteServerId()).isNotBlank();
        assertThat(sub.getStatus()).isEqualTo("active");

        mockMvc.perform(post("/hooks/vk/" + channel.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"confirmation\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(sub.getConfirmationCode()));

        String secret = tokenVault.unseal(fx.workspaceId(), sub.getSecretEnc());
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "wall_post_new");
        event.put("event_id", "dedup-event-1");
        event.put("group_id", "777001");
        event.put("secret", secret);
        ObjectNode object = event.putObject("object");
        object.put("id", 42);
        object.put("from_id", 100);
        object.put("owner_id", -777001);
        object.put("date", Instant.now().getEpochSecond());
        object.put("post_type", "suggest");
        object.put("text", "suggested");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/hooks/vk/" + channel.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Retry-Counter", String.valueOf(i))
                            .content(objectMapper.writeValueAsBytes(event)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ok"));
        }

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            drainAndProcess("ingest.vk");
            assertThat(recordingHandler.size()).isEqualTo(1);
        });
        assertThat(recordingHandler.snapshot().get(0).kind().name()).isEqualTo("POST_SUGGESTED");

        event.put("secret", "wrong");
        mockMvc.perform(post("/hooks/vk/" + channel.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(event)))
                .andExpect(status().isUnauthorized());
        assertThat(recordingHandler.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("TG album 3 photos → one event with 3 attachments; one chat in two workspaces → fan-out")
    void telegramAlbumAndFanOut() throws Exception {
        Fixture fx1 = seedWorkspace();
        Fixture fx2 = seedWorkspace();

        TelegramBotEntity bot =
                telegramBotRepository.findByStatusOrderByCreatedAtAsc("active").get(0);

        ChannelEntity c1 = tgChannel(fx1.workspaceId(), bot.getId(), "-100500");
        ChannelEntity c2 = tgChannel(fx2.workspaceId(), bot.getId(), "-100500");
        channelRepository.save(c1);
        channelRepository.save(c2);

        subscriptionService.ensure(c1.getId(), "suggest", List.of("channel_post"));
        subscriptionService.ensure(c2.getId(), "suggest", List.of("channel_post"));

        String mediaGroup = "mg-42";
        for (int i = 0; i < 3; i++) {
            ObjectNode update = objectMapper.createObjectNode();
            update.put("update_id", 1000 + i);
            ObjectNode post = update.putObject("channel_post");
            post.put("message_id", 10 + i);
            post.put("date", Instant.now().getEpochSecond());
            post.put("media_group_id", mediaGroup);
            post.putObject("chat").put("id", -100500).put("type", "channel");
            post.putArray("photo").addObject().put("file_id", "ph-" + i).put("file_size", 10);

            mockMvc.perform(post("/hooks/tg/" + bot.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Telegram-Bot-Api-Secret-Token", bot.getWebhookSecret())
                            .content(objectMapper.writeValueAsBytes(update)))
                    .andExpect(status().isOk());
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            drainAndProcess("ingest.tg");
            assertThat(albumAggregator.pendingCount()).isGreaterThan(0);
        });
        // Force window expiry then flush
        Thread.sleep(3200);
        processor.flushReadyAlbums();

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(recordingHandler.size()).isEqualTo(2));
        assertThat(recordingHandler.snapshot())
                .allSatisfy(e -> assertThat(e.attachments()).hasSize(3));
    }

    @Test
    @DisplayName("Max comment_created normalized; bad secret → 401")
    void maxCommentAndAuth() throws Exception {
        Fixture fx = seedWorkspace();
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(fx.workspaceId());
        channel.setNetwork("max");
        channel.setExternalId("max-chat-1");
        channel.setType("channel");
        channel.setTitle("Max");
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
        body.put("timestamp", Instant.now().toEpochMilli());
        body.putObject("comment")
                .put("id", "c-1")
                .put("text", "spam?")
                .putObject("sender")
                .put("user_id", "u1");

        mockMvc.perform(post("/hooks/max/" + channel.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Max-Bot-Api-Secret", secret)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            drainAndProcess("ingest.max");
            assertThat(recordingHandler.size()).isEqualTo(1);
        });
        assertThat(recordingHandler.snapshot().get(0).kind().name()).isEqualTo("COMMENT_NEW");

        mockMvc.perform(post("/hooks/max/" + channel.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Max-Bot-Api-Secret", "nope")
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("IDOR: foreign channel event-subscription → 404")
    void eventSubscriptionIdor() throws Exception {
        Fixture owner = seedWorkspace();
        Fixture other = seedWorkspace();
        ChannelEntity channel = tgChannel(owner.workspaceId(), null, "-1");
        channelRepository.save(channel);
        subscriptionService.ensure(channel.getId(), "suggest", List.of("channel_post"));

        String otherToken = login(other.email());
        mockMvc.perform(get("/api/v1/channels/" + channel.getId() + "/event-subscription")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private void drainAndProcess(String queue) {
        Object message;
        while ((message = rabbitTemplate.receiveAndConvert(queue)) != null) {
            if (message instanceof SocialInboundCommand command) {
                processor.process(command);
            } else if (message instanceof Map<?, ?> map) {
                // Jackson may deliver LinkedHashMap when type info is absent
                SocialInboundCommand command = objectMapper.convertValue(map, SocialInboundCommand.class);
                processor.process(command);
            }
        }
    }

    private void drainQueue(String queue) {
        while (rabbitTemplate.receiveAndConvert(queue) != null) {
            // discard
        }
    }

    private ChannelEntity tgChannel(UUID workspaceId, UUID botId, String externalId) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(workspaceId);
        channel.setNetwork("tg");
        channel.setExternalId(externalId);
        channel.setType("channel");
        channel.setTitle("TG " + externalId);
        channel.setStatus("active");
        channel.setBotId(botId);
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        return channel;
    }

    private Fixture seedWorkspace() throws Exception {
        String email = "be32-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", password, "name", "BE32"))))
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
        String accessToken = login(email, password);
        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        UUID workspaceId = UUID.fromString(list.get("items").get(0).get("id").asText());
        return new Fixture(email, password, workspaceId, accessToken);
    }

    private String login(String email) throws Exception {
        return login(email, "correct-horse-battery");
    }

    private String login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(login.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
    }

    private record Fixture(String email, String password, UUID workspaceId, String accessToken) {}
}
