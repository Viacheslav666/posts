package ru.posts.api.suggest;

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
import java.util.Locale;
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
import org.springframework.beans.factory.annotation.Value;
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
import ru.posts.persistence.entity.ProjectEntity;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.ProjectRepository;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.social.inbound.SocialInboundProcessor;
import ru.posts.social.inbound.subscription.EventSubscriptionService;
import ru.posts.suggest.SuggestProcessingService;
import ru.posts.suggest.bulk.SuggestBulkProcessor;
import ru.posts.suggest.config.SuggestConfigSupport;
import ru.posts.suggest.sync.SuggestSyncService;
import ru.posts.testsupport.emulator.SocialEmulatorProcess;

/**
 * BE-34 integration: VK suggests via emulator (never api.vk.com).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SuggestVkIT {

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
            registry.add("posts.social.vk.base-url", emulator::baseUrl);
            registry.add("posts.social-inbound.public-base-url", () -> "http://127.0.0.1:8080");
            registry.add("posts.public-base-url", () -> "http://127.0.0.1:8080");
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
    SocialAccountRepository socialAccountRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    SuggestSettingsRepository settingsRepository;

    @Autowired
    SuggestItemRepository itemRepository;

    @Autowired
    WorkspaceTokenVault tokenVault;

    @Autowired
    EventSubscriptionService subscriptionService;

    @Autowired
    SocialInboundProcessor processor;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    SuggestProcessingService processingService;

    @Autowired
    SuggestSyncService syncService;

    @Autowired
    SuggestBulkProcessor bulkProcessor;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Value("${posts.social.vk.base-url:}")
    String vkBaseUrl;

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
        emulator.reset();
        drainQueue("ingest.vk");
    }

    @Test
    @DisplayName("live API guard: VK base-url must not contain api.vk.com")
    void liveApiGuard() {
        assertThat(vkBaseUrl.toLowerCase(Locale.ROOT)).doesNotContain("api.vk.com");
        assertThat(emulator.baseUrl().toLowerCase(Locale.ROOT)).doesNotContain("api.vk.com");
    }

    @Test
    @DisplayName("POST_SUGGESTED → item → publish_suggested with signed + first comment")
    void callbackPublishSignedAndComment() throws Exception {
        Fixture fx = seedWorkspace();
        ChannelEntity channel = vkChannel(fx, "-700101");
        SuggestSettingsEntity settings = activeSettings(
                fx,
                channel,
                Map.of(
                        "signed",
                        true,
                        "first_comment",
                        Map.of("enabled", true, "text", "first!"),
                        "reject_policy",
                        "keep"));

        subscriptionService.ensure(channel.getId(), "suggest", List.of("wall_post_new"));
        var sub = subscriptionService.getRequired(channel.getId());
        String secret = tokenVault.unseal(fx.workspaceId(), sub.getSecretEnc());

        emulator.put(
                "/_control/vk/suggests",
                """
                {"ownerId":"%s","items":[{"id":501,"text":"please approve"}]}
                """
                        .formatted(channel.getExternalId()));

        postSuggestCallback(channel, secret, "evt-pub-1", 501, "55", "please approve");
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            drainAndProcess("ingest.vk");
            assertThat(itemRepository.findByChannelIdAndVkPostId(channel.getId(), "501"))
                    .isPresent();
        });

        SuggestItemEntity item = itemRepository
                .findByChannelIdAndVkPostId(channel.getId(), "501")
                .orElseThrow();
        assertThat(item.getStatus()).isIn("scheduled", "publishing", "published");
        assertThat(item.getRemoteActionId()).isNotNull();

        Integer publishCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM social_remote_actions
                WHERE channel_id = ? AND kind = 'publish_suggested'
                  AND idempotency_key = ?
                """,
                Integer.class,
                channel.getId(),
                "suggest:" + channel.getId() + ":501");
        assertThat(publishCount).isEqualTo(1);

        Integer commentCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM social_remote_actions
                WHERE channel_id = ? AND kind = 'reply_comment'
                  AND idempotency_key = ?
                """,
                Integer.class,
                channel.getId(),
                "suggest-comment:" + channel.getId() + ":501");
        assertThat(commentCount).isEqualTo(1);

        String payload = jdbcTemplate.queryForObject(
                """
                SELECT payload::text FROM social_remote_actions
                WHERE id = ?
                """,
                String.class,
                item.getRemoteActionId());
        assertThat(payload).isNotBlank();
        assertThat(payload.replace(" ", "")).contains("\"signed\":true");
        assertThat(settings.getId()).isNotNull();
    }

    @Test
    @DisplayName("stop word → rejected + delete_post")
    void stopWordRejectDelete() throws Exception {
        Fixture fx = seedWorkspace();
        ChannelEntity channel = vkChannel(fx, "-700102");
        activeSettings(
                fx,
                channel,
                Map.of(
                        "filters",
                        Map.of("stop_words", List.of("розыгрыш")),
                        "reject_policy",
                        "delete",
                        "notify_author",
                        false));
        subscriptionService.ensure(channel.getId(), "suggest", List.of("wall_post_new"));
        var sub = subscriptionService.getRequired(channel.getId());
        String secret = tokenVault.unseal(fx.workspaceId(), sub.getSecretEnc());

        postSuggestCallback(channel, secret, "evt-stop-1", 601, "77", "Большой розыгрыш призов");
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            drainAndProcess("ingest.vk");
            assertThat(itemRepository.findByChannelIdAndVkPostId(channel.getId(), "601"))
                    .isPresent();
        });
        SuggestItemEntity item = itemRepository
                .findByChannelIdAndVkPostId(channel.getId(), "601")
                .orElseThrow();
        assertThat(item.getStatus()).isIn("rejected", "deleted_remote");
        assertThat(item.getReasons()).contains("stop_words");

        Integer deletes = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM social_remote_actions
                WHERE channel_id = ? AND kind = 'delete_post'
                """,
                Integer.class,
                channel.getId());
        assertThat(deletes).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("author day limit: 3rd → filtered_out")
    void authorDayLimit() {
        Fixture fx = seedWorkspaceJdbc();
        ChannelEntity channel = vkChannelJdbc(fx, "-700103");
        SuggestSettingsEntity settings = activeSettingsJdbc(
                fx, channel, Map.of("limits", Map.of("per_author", Map.of("day", 2)), "reject_policy", "keep"));

        for (int i = 1; i <= 2; i++) {
            processingService
                    .processSuggested(
                            channel.getId(),
                            String.valueOf(700 + i),
                            "88",
                            "post " + i,
                            List.of(),
                            Map.of(),
                            null,
                            null,
                            fx.userId())
                    .ifPresent(item -> {
                        item.setStatus("published");
                        item.setDecidedAt(Instant.now());
                        itemRepository.save(item);
                    });
        }

        SuggestItemEntity third = processingService
                .processSuggested(channel.getId(), "703", "88", "post 3", List.of(), Map.of(), null, null, fx.userId())
                .orElseThrow();
        assertThat(third.getStatus()).isEqualTo("filtered_out");
        assertThat(third.getReasons()).contains("author_limit_day");
        assertThat(settings.getId()).isNotNull();
    }

    @Test
    @DisplayName("triple callback → one item one publish")
    void tripleCallbackIdempotent() throws Exception {
        Fixture fx = seedWorkspace();
        ChannelEntity channel = vkChannel(fx, "-700104");
        activeSettings(fx, channel, Map.of("reject_policy", "keep"));
        subscriptionService.ensure(channel.getId(), "suggest", List.of("wall_post_new"));
        var sub = subscriptionService.getRequired(channel.getId());
        String secret = tokenVault.unseal(fx.workspaceId(), sub.getSecretEnc());

        for (int i = 0; i < 3; i++) {
            postSuggestCallback(channel, secret, "evt-triple-1", 801, "11", "hello suggest");
        }
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            drainAndProcess("ingest.vk");
            assertThat(itemRepository.findByChannelIdAndVkPostId(channel.getId(), "801"))
                    .isPresent();
        });
        assertThat(itemRepository.findByChannelIdAndVkPostId(channel.getId(), "801"))
                .isPresent();

        Integer items = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM suggest_items WHERE channel_id = ? AND vk_post_id = '801'",
                Integer.class,
                channel.getId());
        assertThat(items).isEqualTo(1);

        Integer publishes = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM social_remote_actions
                WHERE channel_id = ? AND kind = 'publish_suggested'
                  AND idempotency_key = ?
                """,
                Integer.class,
                channel.getId(),
                "suggest:" + channel.getId() + ":801");
        assertThat(publishes).isEqualTo(1);
    }

    @Test
    @DisplayName("bulk approve_all → progress 100%, each once")
    void bulkApproveAll() {
        Fixture fx = seedWorkspaceJdbc();
        ChannelEntity channel = vkChannelJdbc(fx, "-700105");
        SuggestSettingsEntity settings =
                activeSettingsJdbc(fx, channel, Map.of("mode", "manual", "reject_policy", "keep"));
        settings.setMode("manual");
        settingsRepository.save(settings);

        int bulkSize = 50;
        for (int i = 0; i < bulkSize; i++) {
            SuggestItemEntity item = new SuggestItemEntity();
            item.setId(UuidV7.generate());
            item.setWorkspaceId(fx.workspaceId());
            item.setChannelId(channel.getId());
            item.setSettingsId(settings.getId());
            item.setVkPostId("9" + i);
            item.setAuthorVkId("1");
            item.setText("pending " + i);
            item.setStatus("pending_moderation");
            item.setCreatedAt(Instant.now());
            item.setUpdatedAt(Instant.now());
            itemRepository.save(item);
        }

        var job = new ru.posts.persistence.entity.SuggestBulkJobEntity();
        job.setId(UuidV7.generate());
        job.setWorkspaceId(fx.workspaceId());
        job.setSettingsId(settings.getId());
        job.setChannelId(channel.getId());
        job.setAction("approve_all");
        job.setStatus("pending");
        job.setIdempotencyKey("bulk-it-" + UuidV7.generate());
        job.setCreatedBy(fx.userId());
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO suggest_bulk_jobs (
                  id, workspace_id, settings_id, channel_id, action, status, idempotency_key,
                  total, processed, succeeded, failed, item_ids, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, '{}', ?, now(), now())
                """,
                job.getId(),
                job.getWorkspaceId(),
                job.getSettingsId(),
                job.getChannelId(),
                job.getAction(),
                job.getStatus(),
                job.getIdempotencyKey(),
                job.getCreatedBy());

        var processed = bulkProcessor.process(job.getId());
        assertThat(processed.getStatus()).isEqualTo("succeeded");
        assertThat(processed.getProcessed()).isEqualTo(processed.getTotal());
        int progress = processed.getTotal() == 0
                ? 100
                : (int) Math.round(100.0 * processed.getProcessed() / processed.getTotal());
        assertThat(progress).isEqualTo(100);

        Integer publishes = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM social_remote_actions
                WHERE channel_id = ? AND kind = 'publish_suggested'
                """,
                Integer.class,
                channel.getId());
        assertThat(publishes).isEqualTo(bulkSize);
    }

    @Test
    @DisplayName("sync marks missing suggest as removed_externally")
    void syncRemovedExternally() throws Exception {
        Fixture fx = seedWorkspaceJdbc();
        ChannelEntity channel = vkChannelJdbc(fx, "-700106");
        attachUserToken(channel, fx.workspaceId());
        SuggestSettingsEntity settings = activeSettingsJdbc(fx, channel, Map.of("reject_policy", "keep"));

        SuggestItemEntity item = new SuggestItemEntity();
        item.setId(UuidV7.generate());
        item.setWorkspaceId(fx.workspaceId());
        item.setChannelId(channel.getId());
        item.setSettingsId(settings.getId());
        item.setVkPostId("4040");
        item.setAuthorVkId("1");
        item.setText("gone");
        item.setStatus("pending_moderation");
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);

        emulator.put(
                "/_control/vk/suggests",
                """
                {"ownerId":"%s","items":[]}
                """.formatted(channel.getExternalId()));

        var result = syncService.syncChannel(settings.getId(), 100);
        assertThat(result.status()).isNotEqualTo("missing_token");

        SuggestItemEntity updated = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("removed_externally");
    }

    @Test
    @DisplayName("near-duplicate of published text → filtered_out")
    void nearDuplicateFilteredOut() {
        Fixture fx = seedWorkspaceJdbc();
        ChannelEntity channel = vkChannelJdbc(fx, "-700108");
        activeSettingsJdbc(
                fx,
                channel,
                Map.of(
                        "dedup",
                        Map.of("enabled", true, "lookback_days", 7, "similarity_threshold", 0.8),
                        "reject_policy",
                        "keep"));

        String text = "Уникальный текст предложки для антидубля BE-34 номер сорок два";
        processingService
                .processSuggested(channel.getId(), "8101", "42", text, List.of(), Map.of(), null, null, fx.userId())
                .ifPresent(item -> {
                    item.setStatus("published");
                    item.setDecidedAt(Instant.now());
                    itemRepository.save(item);
                });

        SuggestItemEntity dup = processingService
                .processSuggested(channel.getId(), "8102", "43", text, List.of(), Map.of(), null, null, fx.userId())
                .orElseThrow();
        assertThat(dup.getStatus()).isEqualTo("filtered_out");
        assertThat(dup.getReasons()).contains("duplicate_content");
    }

    @Test
    @DisplayName("ads pause delays next slot after marked_as_ads publish")
    void adsPauseDelaysSlot() {
        Fixture fx = seedWorkspaceJdbc();
        ChannelEntity channel = vkChannelJdbc(fx, "-700109");
        SuggestSettingsEntity settings = activeSettingsJdbc(
                fx,
                channel,
                Map.of(
                        "ads_pause",
                        Map.of("enabled", true, "pause_after_ads_minutes", 60, "detect_marked_as_ads", true),
                        "reject_policy",
                        "keep"));

        Instant publishedAt = Instant.now().minusSeconds(30);
        SuggestItemEntity ads = new SuggestItemEntity();
        ads.setId(UuidV7.generate());
        ads.setWorkspaceId(fx.workspaceId());
        ads.setChannelId(channel.getId());
        ads.setSettingsId(settings.getId());
        ads.setVkPostId("8200");
        ads.setAuthorVkId("1");
        ads.setText("promo");
        ads.setStatus("published");
        ads.setRaw(Map.of("marked_as_ads", 1));
        ads.setDecidedAt(publishedAt);
        ads.setScheduledFor(publishedAt);
        ads.setCreatedAt(publishedAt);
        ads.setUpdatedAt(publishedAt);
        itemRepository.save(ads);

        SuggestItemEntity next = processingService
                .processSuggested(
                        channel.getId(),
                        "8201",
                        "2",
                        "normal suggest after ads",
                        List.of(),
                        Map.of(),
                        null,
                        null,
                        fx.userId())
                .orElseThrow();
        assertThat(next.getStatus()).isIn("scheduled", "publishing");
        assertThat(next.getScheduledFor()).isAfter(Instant.now().plusSeconds(50 * 60));
    }

    @Test
    @DisplayName("IDOR: foreign settings → 404")
    void idorNotFound() throws Exception {
        Fixture owner = seedWorkspace();
        Fixture other = seedWorkspace();
        ChannelEntity channel = vkChannel(owner, "-700107");
        SuggestSettingsEntity settings = activeSettings(owner, channel, Map.of());

        mockMvc.perform(get("/api/v1/suggest/settings/" + settings.getId())
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private void postSuggestCallback(
            ChannelEntity channel, String secret, String eventId, long postId, String fromId, String text)
            throws Exception {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "wall_post_new");
        event.put("event_id", eventId);
        event.put("group_id", channel.getExternalId().replace("-", ""));
        event.put("secret", secret);
        ObjectNode object = event.putObject("object");
        object.put("id", postId);
        object.put("from_id", Long.parseLong(fromId));
        object.put("owner_id", Long.parseLong(channel.getExternalId()));
        object.put("date", Instant.now().getEpochSecond());
        object.put("post_type", "suggest");
        object.put("text", text);

        mockMvc.perform(post("/hooks/vk/" + channel.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(event)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    private ChannelEntity vkChannel(Fixture fx, String externalId) {
        ChannelEntity channel = new ChannelEntity();
        channel.setId(UuidV7.generate());
        channel.setWorkspaceId(fx.workspaceId());
        channel.setProjectId(fx.projectId());
        channel.setNetwork("vk");
        channel.setExternalId(externalId);
        channel.setType("group");
        channel.setTitle("VK suggest " + externalId);
        channel.setStatus("active");
        channel.setCapabilities(Map.of("publishSuggested", true));
        channel.setGroupTokenEnc(tokenVault.seal(fx.workspaceId(), "emulator-group-token"));
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        return channelRepository.save(channel);
    }

    private ChannelEntity vkChannelJdbc(Fixture fx, String externalId) {
        return vkChannel(fx, externalId);
    }

    private void attachUserToken(ChannelEntity channel, UUID workspaceId) {
        SocialAccountEntity account = new SocialAccountEntity();
        account.setId(UuidV7.generate());
        account.setWorkspaceId(workspaceId);
        account.setNetwork("vk");
        account.setExternalUserId("vk-user-" + channel.getExternalId());
        account.setDisplayName("Suggest admin");
        account.setAccessTokenEnc(tokenVault.seal(workspaceId, "emulator-user-token"));
        account.setScopes(new String[] {"wall", "groups"});
        account.setStatus("active");
        Instant now = Instant.now();
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        socialAccountRepository.save(account);
        channel.setSocialAccountId(account.getId());
        channelRepository.save(channel);
    }

    private SuggestSettingsEntity activeSettings(
            Fixture fx, ChannelEntity channel, Map<String, Object> configOverrides) {
        SuggestSettingsEntity settings = new SuggestSettingsEntity();
        settings.setId(UuidV7.generate());
        settings.setWorkspaceId(fx.workspaceId());
        settings.setProjectId(fx.projectId());
        settings.setChannelId(channel.getId());
        settings.setStatus("active");
        Object mode = configOverrides.getOrDefault("mode", "auto");
        settings.setMode(String.valueOf(mode));
        Map<String, Object> cfg = new java.util.LinkedHashMap<>(configOverrides);
        cfg.remove("mode");
        settings.setConfig(SuggestConfigSupport.withDefaults(cfg));
        settings.setConfigVersion(1);
        settings.setCreatedBy(fx.userId());
        settings.setCreatedAt(Instant.now());
        settings.setUpdatedAt(Instant.now());
        settings.setNextSyncAt(Instant.now().plusSeconds(600));
        return settingsRepository.save(settings);
    }

    private SuggestSettingsEntity activeSettingsJdbc(
            Fixture fx, ChannelEntity channel, Map<String, Object> configOverrides) {
        return activeSettings(fx, channel, configOverrides);
    }

    private Fixture seedWorkspace() throws Exception {
        String email = "be34-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", password, "name", "BE34"))))
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
        String accessToken = objectMapper
                .readTree(login.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
        JsonNode list = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        UUID workspaceId = UUID.fromString(list.get("items").get(0).get("id").asText());
        UUID userId = UUID.fromString(objectMapper
                .readTree(mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("id")
                .asText());
        ProjectEntity project = new ProjectEntity();
        project.setId(UuidV7.generate());
        project.setWorkspaceId(workspaceId);
        project.setName("Suggest Project");
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return new Fixture(userId, workspaceId, project.getId(), accessToken);
    }

    private Fixture seedWorkspaceJdbc() {
        try {
            return seedWorkspace();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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

    private record Fixture(UUID userId, UUID workspaceId, UUID projectId, String accessToken) {}
}
