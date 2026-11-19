package ru.posts.api.qa14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ru.posts.antispam.service.AntispamEventProcessor;
import ru.posts.api.antispam.AntispamService;
import ru.posts.api.error.ApiException;
import ru.posts.api.web.dto.antispam.AntispamDtos;
import ru.posts.common.uuid.UuidV7;
import ru.posts.crosspost.handler.CrosspostEventHandler;
import ru.posts.crosspost.process.CrosspostItemProcessor;
import ru.posts.persistence.entity.AntispamRuleEntity;
import ru.posts.persistence.entity.CrosspostRuleEntity;
import ru.posts.persistence.entity.CrosspostRuleTargetEntity;
import ru.posts.persistence.jdbc.AntispamLogJdbc;
import ru.posts.persistence.repository.AntispamRuleRepository;
import ru.posts.persistence.repository.CrosspostItemRepository;
import ru.posts.persistence.repository.CrosspostRuleRepository;
import ru.posts.persistence.repository.CrosspostRuleTargetRepository;
import ru.posts.social.inbound.HandlerContext;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundAuthor;
import ru.posts.social.inbound.SocialInboundEvent;

/** QA-14 antispam + crosspost filter acceptance beyond {@link ru.posts.api.antispam.AntispamProcessorIT}. */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("ci")
class Qa14AntispamIT {

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
    AntispamRuleRepository ruleRepository;

    @Autowired
    AntispamEventProcessor processor;

    @Autowired
    AntispamLogJdbc logJdbc;

    @Autowired
    AntispamService antispamService;

    @Autowired
    CrosspostRuleRepository crosspostRuleRepository;

    @Autowired
    CrosspostRuleTargetRepository crosspostTargetRepository;

    @Autowired
    CrosspostItemRepository crosspostItemRepository;

    @Autowired
    CrosspostEventHandler crosspostHandler;

    @Autowired
    CrosspostItemProcessor crosspostItemProcessor;

    UUID workspaceId;
    UUID projectId;
    UUID userId;
    UUID channelId;
    UUID ruleId;

    @BeforeEach
    void seed() {
        userId = UuidV7.generate();
        workspaceId = UuidV7.generate();
        projectId = UuidV7.generate();
        channelId = UuidV7.generate();
        ruleId = UuidV7.generate();
        UUID planId = jdbc.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);
        jdbc.update(
                "INSERT INTO users (id, email, name) VALUES (?, ?, ?)", userId, "qa14-as-" + userId + "@ex.com", "AS");
        jdbc.update(
                """
                INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status)
                VALUES (?, 'AS', ?, ?, 'trial')
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
                VALUES (?, ?, 'p', now(), now())
                """,
                projectId,
                workspaceId);
        jdbc.update(
                """
                INSERT INTO channels (id, workspace_id, project_id, network, external_id, type, title, story_connection_status)
                VALUES (?, ?, ?, 'vk', '-1003601', 'group', 'g', 'ready')
                """,
                channelId,
                workspaceId,
                projectId);
        AntispamRuleEntity rule = baseRule();
        rule.setConfig(Map.of(
                "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                "actions", Map.of("delete", true, "ban", Map.of("enabled", true, "days", 7))));
        ruleRepository.save(rule);
    }

    @Test
    @DisplayName("TC-AS-02: restore → antispam_log state restored")
    void restoreSetsRestoredStatus() {
        processor.process(commentEvent("901", "купите спам"));
        var logId = logJdbc.findIdByDedup(channelId, "wall_comment", "wall_comment:-1003601:10:901")
                .orElseThrow();
        var restored = antispamService.restore(userId, logId, new AntispamDtos.AntispamRestoreRequest(false));
        assertThat(restored.state()).isEqualTo("restored");
    }

    @Test
    @DisplayName("TC-AS-04: admin author skipped — no log row")
    void adminAuthorSkipped() {
        AntispamRuleEntity rule = ruleRepository.findById(ruleId).orElseThrow();
        rule.setConfig(Map.of(
                "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                "actions", Map.of("delete", true),
                "exceptions", Map.of("admin_ids", List.of("42"))));
        rule.setConfigVersion(rule.getConfigVersion() + 1);
        ruleRepository.save(rule);
        processor.process(commentEvent("902", "спам от админа", "42"));
        assertThat(logJdbc.findIdByDedup(channelId, "wall_comment", "wall_comment:-1003601:10:902"))
                .isEmpty();
    }

    @Test
    @DisplayName("TC-AS-05: comment under ads with filter off → skipped")
    void commentUnderAdsRequiresFilter() {
        processor.process(commentEvent("903", "ok", "1", true));
        assertThat(logJdbc.findIdByDedup(channelId, "wall_comment", "wall_comment:-1003601:10:903"))
                .isEmpty();
        AntispamRuleEntity rule = ruleRepository.findById(ruleId).orElseThrow();
        rule.setConfig(Map.of(
                "filters",
                        Map.of(
                                "keywords", Map.of("black", List.of("спам")),
                                "context", Map.of("under_ads", true)),
                "actions", Map.of("delete", true)));
        rule.setConfigVersion(rule.getConfigVersion() + 1);
        ruleRepository.save(rule);
        processor.process(commentEvent("904", "спам под рекламой", "1", true));
        assertThat(logJdbc.findIdByDedup(channelId, "wall_comment", "wall_comment:-1003601:10:904"))
                .isPresent();
    }

    @Test
    @DisplayName("TC-AS-09: TG comment older than 48h → failed tg_delete_window_expired")
    void tgStaleCommentFailsWindow() {
        jdbc.update("UPDATE channels SET network = 'tg', external_id = '-1003602' WHERE id = ?", channelId);
        AntispamRuleEntity rule = ruleRepository.findById(ruleId).orElseThrow();
        rule.setSections(List.of("tg_discussion"));
        rule.setConfig(Map.of("filters", Map.of("links", Map.of("all", true)), "actions", Map.of("delete", true)));
        rule.setConfigVersion(rule.getConfigVersion() + 1);
        ruleRepository.save(rule);
        SocialInboundEvent stale = new SocialInboundEvent(
                "tg",
                channelId,
                workspaceId,
                SocialEventKind.MESSAGE_NEW,
                "tg-stale",
                Map.of("chat_id", "-1003602", "message_id", "99"),
                new SocialInboundAuthor("9", false, false),
                "https://spam.test",
                null,
                List.of(),
                Instant.now().minusSeconds(49 * 3600),
                Instant.now(),
                "{\"message\":{\"text\":\"https://spam.test\"}}",
                List.of("antispam"));
        processor.process(stale);
        var log = logJdbc.findById(logJdbc.findIdByDedup(channelId, "tg_discussion", "tg:-1003602:99")
                        .orElseThrow())
                .orElseThrow();
        assertThat(log.state()).isEqualTo("failed");
        assertThat(log.action()).isEqualTo("deleted");
    }

    @Test
    @DisplayName("TC-AS-14: activate without VK user token → 422 antispam_vk_permissions")
    void vkActivateWithoutTokenReturns422() {
        AntispamRuleEntity rule = baseRule();
        rule.setId(UuidV7.generate());
        rule.setStatus("paused");
        ruleRepository.save(rule);
        assertThatThrownBy(() -> antispamService.activate(userId, rule.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("user access token");
    }

    @Test
    @DisplayName("TC-XP-12: crosspost erid text filtered before publication")
    void crosspostEridExcluded() {
        UUID src = channelId;
        UUID dst = UuidV7.generate();
        jdbc.update(
                """
        INSERT INTO channels (id, workspace_id, project_id, network, external_id, type, title, story_connection_status)
        VALUES (?, ?, ?, 'tg', '-1003603', 'channel', 'tg', 'ready')
        """,
                dst,
                workspaceId,
                projectId);
        CrosspostRuleEntity xp = new CrosspostRuleEntity();
        xp.setId(UuidV7.generate());
        xp.setWorkspaceId(workspaceId);
        xp.setProjectId(projectId);
        xp.setSourceChannelId(src);
        xp.setName("xp-erid");
        xp.setStatus("active");
        xp.setConfig(Map.of("delay_minutes", 0));
        xp.setConfigVersion(1);
        xp.setCreatedBy(userId);
        xp.setCreatedAt(Instant.now());
        xp.setUpdatedAt(Instant.now());
        crosspostRuleRepository.save(xp);
        CrosspostRuleTargetEntity target = new CrosspostRuleTargetEntity();
        target.setId(UuidV7.generate());
        target.setRuleId(xp.getId());
        target.setTargetChannelId(dst);
        target.setOptions(Map.of());
        target.setCreatedAt(Instant.now());
        target.setUpdatedAt(Instant.now());
        crosspostTargetRepository.save(target);
        SocialInboundEvent event = new SocialInboundEvent(
                "vk",
                src,
                workspaceId,
                SocialEventKind.POST_NEW,
                "dedup-erid",
                Map.of("post_id", "erid-1"),
                new SocialInboundAuthor("1", false, false),
                "Реклама erid=AbCdEf",
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                "{\"text\":\"erid=AbCdEf\"}",
                List.of("crosspost"));
        crosspostHandler.handle(event, new HandlerContext(Map.of()));
        var item = crosspostItemRepository
                .findByRuleIdAndSourceRemoteId(xp.getId(), "erid-1")
                .orElseThrow();
        item.setProcessAfter(Instant.now());
        crosspostItemRepository.save(item);
        crosspostItemProcessor.processDue(item);
        item = crosspostItemRepository.findById(item.getId()).orElseThrow();
        assertThat(item.getStatus()).isEqualTo("filtered_out");
        assertThat(item.getReasons()).contains("ad_post_excluded");
    }

    private AntispamRuleEntity baseRule() {
        AntispamRuleEntity rule = new AntispamRuleEntity();
        rule.setId(ruleId);
        rule.setWorkspaceId(workspaceId);
        rule.setProjectId(projectId);
        rule.setChannelId(channelId);
        rule.setName("qa14");
        rule.setStatus("active");
        rule.setPriority(10);
        rule.setSections(List.of("wall_comment"));
        rule.setConfigVersion(1);
        rule.setDryRun(false);
        rule.setCreatedBy(userId);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        return rule;
    }

    private SocialInboundEvent commentEvent(String commentId, String text) {
        return commentEvent(commentId, text, "42", false);
    }

    private SocialInboundEvent commentEvent(String commentId, String text, String fromId) {
        return commentEvent(commentId, text, fromId, false);
    }

    private SocialInboundEvent commentEvent(String commentId, String text, String fromId, boolean underAds) {
        String raw =
                """
        {"type":"wall_reply_new","group_id":1003601,"event_id":"e-%s","object":{"id":%s,"post_id":10,"from_id":%s,"owner_id":-1003601,"text":"%s","date":%d}}
        """
                        .formatted(
                                commentId,
                                commentId,
                                fromId,
                                text,
                                Instant.now().getEpochSecond());
        Map<String, String> keys = new java.util.LinkedHashMap<>();
        keys.put("event_id", "e-" + commentId);
        keys.put("group_id", "1003601");
        keys.put("comment_id", commentId);
        keys.put("post_id", "10");
        keys.put("owner_id", "-1003601");
        if (underAds) {
            keys.put("marked_as_ads", "1");
        }
        return new SocialInboundEvent(
                "vk",
                channelId,
                workspaceId,
                SocialEventKind.COMMENT_NEW,
                "e-" + commentId,
                keys,
                new SocialInboundAuthor(fromId, false, fromId.equals("42")),
                text,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                raw,
                List.of("antispam"));
    }
}
