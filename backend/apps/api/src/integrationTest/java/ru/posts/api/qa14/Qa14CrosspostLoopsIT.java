package ru.posts.api.qa14;

import static org.assertj.core.api.Assertions.assertThat;

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

    @BeforeEach
    void seed() {
        userId = UuidV7.generate();
        workspaceId = UuidV7.generate();
        projectId = UuidV7.generate();
        channelA = UuidV7.generate();
        channelB = UuidV7.generate();
        channelC = UuidV7.generate();

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

        insertChannel(channelA, "-910001", "VK A");
        insertChannel(channelB, "-910002", "VK B");
        insertChannel(channelC, "-910003", "VK C");
    }

    @Test
    @DisplayName("TC-XP-loop-ab: A→B publishes once, B→A blocked (0 reverse)")
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
        assertThat(countPublicationsToChannel(channelA)).isZero();

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
        assertThat(itemBa.getStatus()).isIn("skipped_loop", "skipped_own");
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
    }

    @Test
    @DisplayName("TC-XP-triangle: A→B→C→A → one copy in B and C, 0 in A")
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
        assertThat(itemCa.getStatus()).isIn("skipped_loop", "skipped_own");
        assertThat(countPublicationsToChannel(channelA)).isZero();
    }

    private static String rootOrigin(UUID channelId, String remoteId) {
        return """
                {"network":"vk","channel_id":"%s","remote_post_id":"%s"}
                """
                .formatted(channelId, remoteId);
    }

    @Test
    @DisplayName("SocialLiveApiGuard: no production social hosts in test profile")
    void liveApiGuardSmoke() {
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
        return jdbc.queryForObject(
                "SELECT count(*) FROM publications WHERE channel_id = ?", Integer.class, channelId);
    }

    private void insertChannel(UUID id, String externalId, String title) {
        jdbc.update(
                """
                INSERT INTO channels (id, workspace_id, project_id, network, external_id, type, title, story_connection_status)
                VALUES (?, ?, ?, 'vk', ?, 'group', ?, 'ready')
                """,
                id,
                workspaceId,
                projectId,
                externalId,
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
