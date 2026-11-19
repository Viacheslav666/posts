package ru.posts.api.antispam;

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
import ru.posts.api.error.ApiException;
import ru.posts.api.web.dto.antispam.AntispamDtos;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.AntispamRuleEntity;
import ru.posts.persistence.jdbc.AntispamLogJdbc;
import ru.posts.persistence.repository.AntispamRuleRepository;
import ru.posts.social.inbound.HandlerResult;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundAuthor;
import ru.posts.social.inbound.SocialInboundEvent;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("ci")
class AntispamProcessorIT {

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
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration");
        registry.add("posts.social-inbound.recording-handler", () -> "false");
        registry.add("posts.social-inbound.public-base-url", () -> "http://127.0.0.1:8080");
        registry.add("posts.public-base-url", () -> "http://127.0.0.1:8080");
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

    UUID workspaceId;
    UUID projectId;
    UUID channelId;
    UUID userId;
    UUID ruleId;

    @BeforeEach
    void seed() {
        userId = UuidV7.generate();
        workspaceId = UuidV7.generate();
        projectId = UuidV7.generate();
        channelId = UuidV7.generate();
        ruleId = UuidV7.generate();

        UUID planId = jdbc.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);
        jdbc.update("INSERT INTO users (id, email, name) VALUES (?, ?, ?)", userId, "as-" + userId + "@ex.com", "AS");
        jdbc.update(
                """
                INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status)
                VALUES (?, 'AS WS', ?, ?, 'trial')
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
        UUID accountId = UuidV7.generate();
        jdbc.update(
                """
                INSERT INTO social_accounts (id, workspace_id, network, external_user_id, display_name, access_token_enc)
                VALUES (?, ?, 'vk', 'u1', 'u', E'\\\\xDEAD')
                """,
                accountId,
                workspaceId);
        jdbc.update(
                """
                INSERT INTO channels (id, workspace_id, project_id, social_account_id, network, external_id, type, title, story_connection_status)
                VALUES (?, ?, ?, ?, 'vk', '-1003501', 'group', 'g', 'ready')
                """,
                channelId,
                workspaceId,
                projectId,
                accountId);

        AntispamRuleEntity rule = new AntispamRuleEntity();
        rule.setId(ruleId);
        rule.setWorkspaceId(workspaceId);
        rule.setProjectId(projectId);
        rule.setChannelId(channelId);
        rule.setName("stop");
        rule.setStatus("active");
        rule.setPriority(10);
        rule.setSections(List.of("wall_comment"));
        rule.setConfig(Map.of(
                "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                "actions",
                        Map.of(
                                "delete",
                                true,
                                "reply",
                                Map.of("enabled", true, "template", "удалено: {reasons}"),
                                "ban",
                                Map.of("enabled", true, "days", 7, "reason", "spam", "comment_visible", false))));
        rule.setConfigVersion(1);
        rule.setDryRun(false);
        rule.setCreatedBy(userId);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        ruleRepository.save(rule);
    }

    @Test
    @DisplayName("VK stop-word → reply+delete+ban; idempotent replay; dry-run flags")
    void vkStopWordActionsAndIdempotency() {
        SocialInboundEvent event = commentEvent("91", "купите спам");
        assertThat(processor.process(event)).isEqualTo(HandlerResult.CONTINUE);

        Integer actions = jdbc.queryForObject(
                "SELECT count(*) FROM social_remote_actions WHERE channel_id = ? AND source = 'antispam'",
                Integer.class,
                channelId);
        assertThat(actions).isEqualTo(3);
        assertThat(logJdbc.findIdByDedup(channelId, "wall_comment", "wall_comment:-1003501:10:91"))
                .isPresent();

        processor.process(event);
        processor.process(event);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM social_remote_actions WHERE channel_id = ? AND source = 'antispam'",
                        Integer.class,
                        channelId))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM antispam_log WHERE channel_id = ?", Integer.class, channelId))
                .isEqualTo(1);

        AntispamRuleEntity dry = ruleRepository.findById(ruleId).orElseThrow();
        dry.setDryRun(true);
        dry.setConfigVersion(dry.getConfigVersion() + 1);
        ruleRepository.save(dry);
        processor.process(commentEvent("92", "ещё спам"));
        var dryLog = logJdbc.findById(logJdbc.findIdByDedup(channelId, "wall_comment", "wall_comment:-1003501:10:92")
                        .orElseThrow())
                .orElseThrow();
        assertThat(dryLog.action()).isEqualTo("flagged");
        assertThat(dryLog.remoteActionIds()).isEmpty();
    }

    @Test
    @DisplayName("TG auto-forward skipped; restore 422; Max restorable=false")
    void tgMaxConstraints() {
        jdbc.update("UPDATE channels SET network = 'tg', external_id = '-1001' WHERE id = ?", channelId);
        AntispamRuleEntity rule = ruleRepository.findById(ruleId).orElseThrow();
        rule.setSections(List.of("tg_discussion"));
        rule.setConfig(Map.of(
                "filters", Map.of("links", Map.of("all", true)),
                "actions", Map.of("delete", true)));
        rule.setConfigVersion(rule.getConfigVersion() + 1);
        rule = ruleRepository.save(rule);

        SocialInboundEvent fwd = new SocialInboundEvent(
                "tg",
                channelId,
                workspaceId,
                SocialEventKind.MESSAGE_NEW,
                "tg-fwd",
                Map.of("chat_id", "-1001", "message_id", "5", "is_automatic_forward", "true"),
                new SocialInboundAuthor("9", false, false),
                "https://spam.example",
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                "{\"message\":{\"is_automatic_forward\":true,\"text\":\"https://spam.example\"}}",
                List.of("antispam"));
        processor.process(fwd);
        assertThat(logJdbc.findIdByDedup(channelId, "tg_discussion", "tg:-1001:5"))
                .isEmpty();

        SocialInboundEvent msg = new SocialInboundEvent(
                "tg",
                channelId,
                workspaceId,
                SocialEventKind.MESSAGE_NEW,
                "tg-1",
                Map.of("chat_id", "-1001", "message_id", "6"),
                new SocialInboundAuthor("9", false, false),
                "https://spam.example",
                null,
                List.of(),
                Instant.now().minusSeconds(3600),
                Instant.now(),
                "{\"message\":{\"message_id\":6,\"text\":\"https://spam.example\"}}",
                List.of("antispam"));
        processor.process(msg);
        var tgLog = logJdbc.findById(logJdbc.findIdByDedup(channelId, "tg_discussion", "tg:-1001:6")
                        .orElseThrow())
                .orElseThrow();
        assertThat(tgLog.restorable()).isFalse();
        assertThatThrownBy(() ->
                        antispamService.restore(userId, tgLog.id(), new AntispamDtos.AntispamRestoreRequest(false)))
                .isInstanceOf(ApiException.class);

        jdbc.update("UPDATE channels SET network = 'max', external_id = 'chat-1' WHERE id = ?", channelId);
        // Reload: prior save merges a detached entity and the old reference keeps a stale @Version.
        rule = ruleRepository.findById(ruleId).orElseThrow();
        rule.setSections(List.of("max_comment"));
        rule.setConfig(Map.of(
                "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                "actions", Map.of("delete", true)));
        rule.setConfigVersion(rule.getConfigVersion() + 1);
        ruleRepository.save(rule);
        SocialInboundEvent max = new SocialInboundEvent(
                "max",
                channelId,
                workspaceId,
                SocialEventKind.COMMENT_NEW,
                "max-1",
                Map.of("mid", "m1", "comment_id", "c1"),
                new SocialInboundAuthor("u", false, false),
                "это спам",
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                "{\"update_type\":\"comment_created\"}",
                List.of("antispam"));
        processor.process(max);
        var maxLog = logJdbc.findById(logJdbc.findIdByDedup(channelId, "max_comment", "max:m1:c1")
                        .orElseThrow())
                .orElseThrow();
        assertThat(maxLog.restorable()).isFalse();
        assertThat(maxLog.action()).isEqualTo("deleted");
    }

    private SocialInboundEvent commentEvent(String commentId, String text) {
        String raw =
                """
                {"type":"wall_reply_new","group_id":1003501,"event_id":"e-%s","object":{"id":%s,"post_id":10,"from_id":42,"owner_id":-1003501,"text":"%s","date":%d}}
                """
                        .formatted(commentId, commentId, text, Instant.now().getEpochSecond());
        return new SocialInboundEvent(
                "vk",
                channelId,
                workspaceId,
                SocialEventKind.COMMENT_NEW,
                "e-" + commentId,
                Map.of(
                        "event_id", "e-" + commentId,
                        "group_id", "1003501",
                        "comment_id", commentId,
                        "post_id", "10",
                        "owner_id", "-1003501"),
                new SocialInboundAuthor("42", false, false),
                text,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                raw,
                List.of("antispam"));
    }
}
