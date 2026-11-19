package ru.posts.api.qa14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.common.uuid.UuidV7;
import ru.posts.content.hash.ContentFingerprint;
import ru.posts.crosspost.handler.CrosspostEventHandler;
import ru.posts.crosspost.process.CrosspostItemProcessor;
import ru.posts.ord.api.OrdSandboxGuard;
import ru.posts.persistence.entity.CrosspostRuleEntity;
import ru.posts.persistence.entity.CrosspostRuleTargetEntity;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;
import ru.posts.persistence.repository.CrosspostItemRepository;
import ru.posts.persistence.repository.CrosspostRuleRepository;
import ru.posts.persistence.repository.CrosspostRuleTargetRepository;
import ru.posts.social.api.SocialLiveApiGuard;
import ru.posts.social.inbound.HandlerContext;
import ru.posts.social.inbound.HandlerResult;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundAttachment;
import ru.posts.social.inbound.SocialInboundAuthor;
import ru.posts.social.inbound.SocialInboundEvent;

/**
 * QA-14 / BE-36: crosspost loop and duplicate protection on DB + processor (emulator-free fast IT).
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("ci")
class Qa14CrosspostLoopsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("posts.crypto.kek", () -> "integration-test-kek-material");
        registry.add("posts.crosspost.defer-seconds", () -> "PT0S");
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CrosspostRuleRepository ruleRepository;

    @Autowired
    CrosspostRuleTargetRepository targetRepository;

    @Autowired
    CrosspostItemRepository itemRepository;

    @Autowired
    CrosspostEventHandler crosspostHandler;

    @Autowired
    CrosspostItemProcessor itemProcessor;

    @Autowired
    RemotePostRegistryJdbc registry;

    UUID workspaceId;
    UUID projectId;
    UUID userId;
    UUID channelA;
    UUID channelB;
    UUID channelC;
    UUID channelTg;

    @BeforeEach
    void seed() {
        userId = UuidV7.generate();
        workspaceId = UuidV7.generate();
        projectId = UuidV7.generate();
        channelA = UuidV7.generate();
        channelB = UuidV7.generate();
        channelC = UuidV7.generate();
        channelTg = UuidV7.generate();

        UUID planId = jdbc.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);
        jdbc.update("INSERT INTO users (id, email, name) VALUES (?, ?, ?)", userId, "xp-" + userId + "@ex.com", "XP");
        jdbc.update(
                """
                INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status)
                VALUES (?, 'XP WS', ?, ?, 'trial')
                """,
                workspaceId,
                userId,
                planId);
        jdbc.update(
                "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, 'owner')",
                workspaceId,
                userId);
        jdbc.update(
                """
                INSERT INTO projects (id, workspace_id, name, created_at, updated_at)
                VALUES (?, ?, 'xp', now(), now())
                """,
                projectId,
                workspaceId);

        insertChannel(channelA, "vk", "-910001", "VK A");
        insertChannel(channelB, "vk", "-910002", "VK B");
        insertChannel(channelC, "vk", "-910003", "VK C");
        insertChannel(channelTg, "tg", "-100910004", "TG");
    }

    @Test
    @DisplayName("TC-XP-loop-ab: A→B once; echo on B blocked by own-registry (0 reverse)")
    void loopAbNoReverse() {
        CrosspostRuleEntity ruleAb = activeRule(channelA, "A→B");
        saveTarget(ruleAb.getId(), channelB);
        CrosspostRuleEntity ruleBa = activeRule(channelB, "B→A");
        saveTarget(ruleBa.getId(), channelA);

        String text = "crosspost loop ab " + UuidV7.generate();
        String fp = ContentFingerprint.fingerprint(text, List.of());
        postNew(channelA, "post-1001", text);
        var itemAb = itemRepository
                .findByRuleIdAndSourceRemoteId(ruleAb.getId(), "post-1001")
                .orElseThrow();
        itemAb.setProcessAfter(Instant.now());
        itemRepository.save(itemAb);
        itemProcessor.processDue(itemAb);
        assertThat(countPublicationsToChannel(channelB)).isEqualTo(1);

        String rootOrigin =
                """
                {"network":"vk","channel_id":"%s","remote_post_id":"post-1001"}
                """
                        .formatted(channelA);
        registry.upsert(workspaceId, "vk", channelB, "post-on-b", "crosspost", null, null, rootOrigin, (short) 1, fp);

        postNew(channelB, "post-on-b", text);
        var itemBa = itemRepository
                .findByRuleIdAndSourceRemoteId(ruleBa.getId(), "post-on-b")
                .orElseThrow();
        itemBa.setProcessAfter(Instant.now());
        itemRepository.save(itemBa);
        itemProcessor.processDue(itemBa);
        itemBa = itemRepository.findById(itemBa.getId()).orElseThrow();
        assertThat(itemBa.getStatus()).isEqualTo("skipped_own");
        assertThat(itemBa.getReasons()).contains("own_crosspost");
        assertThat(countPublicationsToChannel(channelA)).isZero();
    }

    @Test
    @DisplayName("TC-XP-race: intent written, registry row not yet committed → 0 reverse")
    void registryLagBlockedByIntent() {
        CrosspostRuleEntity ruleBa = activeRule(channelB, "B→A race");
        saveTarget(ruleBa.getId(), channelA);
        String text = "registry lag " + UuidV7.generate();
        String fp = ContentFingerprint.fingerprint(text, List.of());
        registry.insertIntent(
                channelB, "xp-race-" + UuidV7.generate(), fp, Instant.now().plus(Duration.ofMinutes(10)));

        postNew(channelB, "echo-before-registry", text);
        var item = itemRepository
                .findByRuleIdAndSourceRemoteId(ruleBa.getId(), "echo-before-registry")
                .orElseThrow();
        item.setProcessAfter(Instant.now());
        itemRepository.save(item);
        itemProcessor.processDue(item);
        item = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(item.getStatus()).isEqualTo("skipped_own");
        assertThat(item.getReasons()).contains("own_intent");
        assertThat(countPublicationsToChannel(channelA)).isZero();
    }

    @Test
    @DisplayName("TC-XP-fan-in: A→C and B→C with the same text → one publication on C")
    void fanInOneCopyOnSharedTarget() {
        UUID ruleAc = activeRule(channelA, "A→C").getId();
        saveTarget(ruleAc, channelC);
        UUID ruleBc = activeRule(channelB, "B→C").getId();
        saveTarget(ruleBc, channelC);
        String text = "fan-in shared " + UuidV7.generate();
        String fp = ContentFingerprint.fingerprint(text, List.of());

        postNew(channelA, "fan-a", text);
        processItem(ruleAc, "fan-a");
        assertThat(countPublicationsToChannel(channelC)).isEqualTo(1);
        registry.upsert(
                workspaceId,
                "vk",
                channelC,
                "remote-on-c",
                "crosspost",
                null,
                null,
                rootOrigin(channelA, "fan-a"),
                (short) 1,
                fp);

        postNew(channelB, "fan-b", text);
        var itemBc =
                itemRepository.findByRuleIdAndSourceRemoteId(ruleBc, "fan-b").orElseThrow();
        itemBc.setProcessAfter(Instant.now());
        itemRepository.save(itemBc);
        itemProcessor.processDue(itemBc);
        itemBc = itemRepository.findById(itemBc.getId()).orElseThrow();
        assertThat(itemBc.getStatus()).isEqualTo("skipped_duplicate");
        assertThat(itemBc.getReasons()).contains("exact_fingerprint");
        assertThat(countPublicationsToChannel(channelC)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-XP-oversize: TG file >20MB without Local Bot API → skipped_unsupported_media, 0 pubs")
    void telegramFileOver20MbSkipped() {
        UUID rule = activeRule(channelTg, "TG→A").getId();
        saveTarget(rule, channelA);
        SocialInboundEvent event = new SocialInboundEvent(
                "tg",
                channelTg,
                workspaceId,
                SocialEventKind.POST_NEW,
                "dedup-oversize",
                Map.of("message_id", "oversize-1"),
                new SocialInboundAuthor("1", false, false),
                "big file",
                null,
                List.of(new SocialInboundAttachment(
                        "document", "file-1", null, 21L * 1024 * 1024, "application/octet-stream")),
                Instant.now(),
                Instant.now(),
                "{\"chat\":{\"type\":\"channel\"}}",
                List.of("crosspost"));
        assertThat(crosspostHandler.handle(event, new HandlerContext(Map.of()))).isEqualTo(HandlerResult.CONTINUE);
        processItem(rule, "oversize-1");
        var item =
                itemRepository.findByRuleIdAndSourceRemoteId(rule, "oversize-1").orElseThrow();
        assertThat(item.getStatus()).isEqualTo("skipped_unsupported_media");
        assertThat(item.getReasons()).contains("tg_file_over_20mb");
        assertThat(countPublicationsToChannel(channelA)).isZero();
    }

    @Test
    @DisplayName("TC-XP-dedup-callback: same POST_NEW ×5 → one crosspost item")
    void duplicateInboundFiveTimes() {
        CrosspostRuleEntity rule = activeRule(channelA, "A→B");
        saveTarget(rule.getId(), channelB);
        String text = "dedup five";
        for (int i = 0; i < 5; i++) {
            postNew(channelA, "dedup-remote-77", text);
        }
        assertThat(itemRepository.findByRuleIdAndSourceRemoteId(rule.getId(), "dedup-remote-77"))
                .isPresent();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM crosspost_items WHERE rule_id = ?", Integer.class, rule.getId()))
                .isEqualTo(1);
        processItem(rule.getId(), "dedup-remote-77");
        processItem(rule.getId(), "dedup-remote-77");
        assertThat(countPublicationsToChannel(channelB)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-XP-triangle: B and C each get one copy; echo on C blocked by own-registry (0 back to A)")
    void triangleSingleCopies() {
        UUID ruleAb = activeRule(channelA, "A→B").getId();
        saveTarget(ruleAb, channelB);
        UUID ruleBc = activeRule(channelB, "B→C").getId();
        saveTarget(ruleBc, channelC);
        UUID ruleCa = activeRule(channelC, "C→A").getId();
        saveTarget(ruleCa, channelA);

        postNew(channelA, "tri-1", "triangle post");
        processItem(ruleAb, "tri-1");
        assertThat(countPublicationsToChannel(channelB)).isEqualTo(1);

        String text = "triangle post";
        String fp = ContentFingerprint.fingerprint(text, List.of());
        postNew(channelB, "tri-b", text);
        processItem(ruleBc, "tri-b");
        assertThat(countPublicationsToChannel(channelC)).isEqualTo(1);
        registry.upsert(
                workspaceId,
                "vk",
                channelC,
                "tri-c",
                "crosspost",
                null,
                null,
                rootOrigin(channelA, "tri-1"),
                (short) 2,
                fp);

        postNew(channelC, "tri-c", text);
        var itemCa =
                itemRepository.findByRuleIdAndSourceRemoteId(ruleCa, "tri-c").orElseThrow();
        itemCa.setProcessAfter(Instant.now());
        itemRepository.save(itemCa);
        itemProcessor.processDue(itemCa);
        itemCa = itemRepository.findById(itemCa.getId()).orElseThrow();
        assertThat(itemCa.getStatus()).isEqualTo("skipped_own");
        assertThat(itemCa.getReasons()).contains("own_crosspost");
        assertThat(countPublicationsToChannel(channelA)).isZero();
    }

    private static String rootOrigin(UUID channelId, String remoteId) {
        return """
                {"network":"vk","channel_id":"%s","remote_post_id":"%s"}
                """
                .formatted(channelId, remoteId);
    }

    @Test
    @DisplayName("SocialLiveApiGuard rejects production VK/TG/Max/OK and ORD production host")
    void liveApiGuardRejectsProductionHosts() {
        assertThatThrownBy(() -> SocialLiveApiGuard.assertNotProductionSocialUrl("https://api.vk.com/method/wall.get"))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() ->
                        SocialLiveApiGuard.assertNotProductionSocialUrl("https://api.telegram.org/botx/sendMessage"))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(
                        () -> SocialLiveApiGuard.assertNotProductionSocialUrl("https://platform-api2.max.ru/messages"))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> SocialLiveApiGuard.assertNotProductionSocialUrl("https://api.ok.ru/fb.do"))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> OrdSandboxGuard.assertVkOrdSandboxBaseUrl("https://api.ord.vk.com"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("api.ord.vk.com");
        SocialLiveApiGuard.assertNotProductionSocialUrl("http://127.0.0.1:18090");
    }

    private void processItem(UUID ruleId, String remoteId) {
        var item =
                itemRepository.findByRuleIdAndSourceRemoteId(ruleId, remoteId).orElseThrow();
        item.setProcessAfter(Instant.now());
        itemRepository.save(item);
        itemProcessor.processDue(item);
    }

    private void postNew(UUID channelId, String remoteId, String text) {
        SocialInboundEvent event = new SocialInboundEvent(
                "vk",
                channelId,
                workspaceId,
                SocialEventKind.POST_NEW,
                "dedup-" + remoteId,
                Map.of("post_id", remoteId, "owner_id", externalId(channelId)),
                new SocialInboundAuthor("1", false, false),
                text,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                "{\"id\":" + remoteId + ",\"text\":\"" + text + "\"}",
                List.of("crosspost"));
        assertThat(crosspostHandler.handle(event, new HandlerContext(Map.of()))).isEqualTo(HandlerResult.CONTINUE);
    }

    private String externalId(UUID channelId) {
        return jdbc.queryForObject("SELECT external_id FROM channels WHERE id = ?", String.class, channelId);
    }

    private int countPublicationsToChannel(UUID channelId) {
        return jdbc.queryForObject("SELECT count(*) FROM publications WHERE channel_id = ?", Integer.class, channelId);
    }

    private void insertChannel(UUID id, String network, String externalId, String title) {
        String type = "tg".equals(network) ? "channel" : "group";
        jdbc.update(
                """
                INSERT INTO channels (id, workspace_id, project_id, network, external_id, type, title, story_connection_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ready')
                """,
                id,
                workspaceId,
                projectId,
                network,
                externalId,
                type,
                title);
    }

    private CrosspostRuleEntity activeRule(UUID sourceChannelId, String name) {
        CrosspostRuleEntity rule = new CrosspostRuleEntity();
        rule.setId(UuidV7.generate());
        rule.setWorkspaceId(workspaceId);
        rule.setProjectId(projectId);
        rule.setSourceChannelId(sourceChannelId);
        rule.setName(name);
        rule.setStatus("active");
        rule.setConfig(Map.of("delay_minutes", 0));
        rule.setConfigVersion(1);
        rule.setCreatedBy(userId);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        return ruleRepository.save(rule);
    }

    private void saveTarget(UUID ruleId, UUID targetChannelId) {
        CrosspostRuleTargetEntity target = new CrosspostRuleTargetEntity();
        target.setId(UuidV7.generate());
        target.setRuleId(ruleId);
        target.setTargetChannelId(targetChannelId);
        target.setOptions(Map.of());
        target.setCreatedAt(Instant.now());
        target.setUpdatedAt(Instant.now());
        targetRepository.save(target);
    }
}
