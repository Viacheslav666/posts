package ru.posts.api.qa14;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import ru.posts.api.qa14.support.Qa14EmulatorPublicationDrain;
import ru.posts.common.uuid.UuidV7;
import ru.posts.content.hash.ContentFingerprint;
import ru.posts.crosspost.handler.CrosspostEventHandler;
import ru.posts.crosspost.process.CrosspostItemProcessor;
import ru.posts.persistence.entity.CrosspostRuleEntity;
import ru.posts.persistence.entity.CrosspostRuleTargetEntity;
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
import ru.posts.testsupport.emulator.SocialEmulatorProcess;

/** TC-XP-01…04: crosspost fan-out on social-emulator (never live VK/TG/OK/Max). */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("ci")
class Qa14CrosspostPairsIT {

    private static final String JPEG_B64 =
            "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDAREAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAb/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAGfAP/EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAT8Cf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQIBAT8Cf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQMBAT8Cf//Z";

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
        registry.add("posts.social.vk.base-url", emulator::baseUrl);
        registry.add("posts.social.telegram.base-url", emulator::baseUrl);
        registry.add("posts.social.ok.base-url", emulator::baseUrl);
        registry.add("posts.social.max.base-url", emulator::baseUrl);
        registry.add("posts.crosspost.defer-seconds", () -> "PT0S");
        registry.add("posts.crypto.kek", () -> "integration-test-kek-material");
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
    ObjectMapper objectMapper;

    UUID workspaceId;
    UUID projectId;
    UUID userId;

    @BeforeAll
    void bootEmulator() throws Exception {
        SocialLiveApiGuard.assertNotProductionSocialUrl(emulator.baseUrl());
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
    void resetEmulator() throws Exception {
        emulator.reset();
        userId = UuidV7.generate();
        workspaceId = UuidV7.generate();
        projectId = UuidV7.generate();
        seedWorkspace();
    }

    @Test
    @DisplayName("TC-XP-01: VK text + 3 photos → 1 publication each on TG, OK, Max")
    void vkThreePhotosFanout() throws Exception {
        UUID vk = channel("vk", "-920101", "group", "VK src");
        UUID tg = channel("tg", "-100920102", "channel", "TG");
        UUID ok = channel("ok", "920103", "group", "OK");
        UUID max = channel("max", "max-920104", "channel", "Max");
        UUID ruleId = rule(vk, "vk-fanout", List.of(tg, ok, max));
        List<Map<String, Object>> photos = threePhotos();
        ingestVkPost(vk, "fanout-1", "vk three photos", photos);
        process(ruleId, "fanout-1");
        assertThat(countPubs(tg)).isEqualTo(1);
        assertThat(countPubs(ok)).isEqualTo(1);
        assertThat(countPubs(max)).isEqualTo(1);
        Qa14EmulatorPublicationDrain.publishAllForChannels(
                jdbc, objectMapper, emulator.baseUrl(), List.of(tg, ok, max), "emulator-token");
        assertThat(countEmulatorPosts("telegram")).isEqualTo(1);
        assertThat(countEmulatorPosts("ok")).isEqualTo(1);
        assertThat(countEmulatorPosts("max")).isEqualTo(1);
        assertThat(objectMapper
                        .readTree(emulator.get("/_control/verify"))
                        .path("ok")
                        .asBoolean())
                .isTrue();
    }

    @Test
    @DisplayName("TC-XP-02: TG album 3 photos → 1 VK publication with 3 attachments in variant")
    void tgAlbumToVk() throws Exception {
        UUID tg = channel("tg", "-100920201", "channel", "TG src");
        UUID vk = channel("vk", "-920202", "group", "VK dst");
        UUID ruleId = rule(tg, "tg-vk", List.of(vk));
        ingestTgPost(tg, "album-201", "tg album", threePhotos());
        process(ruleId, "album-201");
        assertThat(countPubs(vk)).isEqualTo(1);
        String variantJson = jdbc.queryForObject(
                """
            SELECT pv.content::text FROM publications p
            JOIN post_variants pv ON pv.id = p.variant_id
            WHERE p.channel_id = ?
            """,
                String.class,
                vk);
        assertThat(variantJson).contains("crosspostAttachments");
        Qa14EmulatorPublicationDrain.publishAllForChannels(
                jdbc, objectMapper, emulator.baseUrl(), List.of(vk), "emulator-token");
        assertThat(countEmulatorPosts("vk")).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-XP-03: Max → TG one-to-one copy")
    void maxToTg() throws Exception {
        UUID max = channel("max", "max-920301", "channel", "Max src");
        UUID tg = channel("tg", "-100920302", "channel", "TG dst");
        UUID ruleId = rule(max, "max-tg", List.of(tg));
        ingestMaxPost(max, "max-301", "from max");
        process(ruleId, "max-301");
        assertThat(countPubs(tg)).isEqualTo(1);
        Qa14EmulatorPublicationDrain.publishAllForChannels(
                jdbc, objectMapper, emulator.baseUrl(), List.of(tg), "emulator-token");
        assertThat(countEmulatorPosts("telegram")).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-XP-04: VK→TG text >1024 with media keeps caption + overflow order")
    void tgLongTextOrderPreserved() {
        UUID vk = channel("vk", "-920401", "group", "VK long src");
        UUID tg = channel("tg", "-100920402", "channel", "TG dst");
        UUID ruleId = rule(vk, "long", List.of(tg));
        String prefix = "A".repeat(900);
        String suffix = "B".repeat(200);
        String full = prefix + suffix;
        ingestVkPost(vk, "long-401", full, threePhotos());
        process(ruleId, "long-401");
        UUID pubId = jdbc.queryForObject("SELECT id FROM publications WHERE channel_id = ? LIMIT 1", UUID.class, tg);
        String content = jdbc.queryForObject(
                """
            SELECT pv.content::text FROM post_variants pv
            JOIN publications p ON p.variant_id = pv.id WHERE p.id = ?
            """,
                String.class,
                pubId);
        assertThat(content).contains("AAAAAAAAAA");
        assertThat(content).contains("followUpText");
        assertThat(content.indexOf("followUpText")).isGreaterThan(content.indexOf("\"text\""));
        assertThat(content).contains(suffix.substring(0, 20));
    }

    @Test
    @DisplayName("TC-XP-12 / L6: erid post filtered (ad_post_excluded)")
    void eridPostSkipped() {
        UUID vk = channel("vk", "-920501", "group", "VK ad");
        UUID tg = channel("tg", "-100920502", "channel", "TG");
        UUID ruleId = rule(vk, "ad-skip", List.of(tg));
        ingestVkPost(vk, "ad-1", "Buy now erid=XPTEST123", List.of());
        var item = itemRepository.findByRuleIdAndSourceRemoteId(ruleId, "ad-1").orElseThrow();
        item.setProcessAfter(Instant.now());
        itemRepository.save(item);
        itemProcessor.processDue(item);
        item = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(item.getStatus()).isEqualTo("filtered_out");
        assertThat(item.getReasons()).contains("ad_post_excluded");
        assertThat(countPubs(tg)).isZero();
    }

    private List<Map<String, Object>> threePhotos() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "photo");
            row.put("remoteRef", "ph-" + i);
            row.put("dataBase64", JPEG_B64);
            row.put("mimeType", "image/jpeg");
            list.add(row);
        }
        return list;
    }

    private void process(UUID ruleId, String remoteId) {
        var item =
                itemRepository.findByRuleIdAndSourceRemoteId(ruleId, remoteId).orElseThrow();
        item.setProcessAfter(Instant.now());
        itemRepository.save(item);
        itemProcessor.processDue(item);
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo("queued");
    }

    private void ingestVkPost(UUID channelId, String remoteId, String text, List<Map<String, Object>> attachments) {
        List<SocialInboundAttachment> atts = attachments.stream()
                .map(a -> new SocialInboundAttachment(
                        String.valueOf(a.get("type")),
                        String.valueOf(a.get("remoteRef")),
                        a.get("url") == null ? null : String.valueOf(a.get("url")),
                        1200L,
                        "image/jpeg"))
                .toList();
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
                atts,
                Instant.now(),
                Instant.now(),
                "{\"text\":\"" + text + "\"}",
                List.of("crosspost"));
        assertThat(crosspostHandler.handle(event, new HandlerContext(Map.of()))).isEqualTo(HandlerResult.CONTINUE);
        itemRepository
                .findByRuleIdAndSourceRemoteId(
                        ruleRepository
                                .findBySourceChannelIdAndStatus(channelId, "active")
                                .getFirst()
                                .getId(),
                        remoteId)
                .ifPresent(item -> {
                    if (!attachments.isEmpty()) {
                        item.setAttachmentsJson(attachments);
                        itemRepository.save(item);
                    }
                });
    }

    private void ingestTgPost(UUID channelId, String remoteId, String text, List<Map<String, Object>> attachments) {
        SocialInboundEvent event = new SocialInboundEvent(
                "tg",
                channelId,
                workspaceId,
                SocialEventKind.POST_NEW,
                "dedup-tg-" + remoteId,
                Map.of("message_id", remoteId, "chat_id", externalId(channelId)),
                new SocialInboundAuthor("1", true, true),
                text,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                "{\"channel_post\":{\"message_id\":" + remoteId + ",\"text\":\"" + text + "\"}}",
                List.of("crosspost"));
        assertThat(crosspostHandler.handle(event, new HandlerContext(Map.of()))).isEqualTo(HandlerResult.CONTINUE);
        var item = itemRepository
                .findByRuleIdAndSourceRemoteId(
                        ruleRepository
                                .findBySourceChannelIdAndStatus(channelId, "active")
                                .getFirst()
                                .getId(),
                        remoteId)
                .orElseThrow();
        if (!attachments.isEmpty()) {
            item.setAttachmentsJson(attachments);
            item.setContentText(text);
            item.setContentFingerprint(ContentFingerprint.fingerprint(text, List.of()));
            itemRepository.save(item);
        }
    }

    private void ingestMaxPost(UUID channelId, String remoteId, String text) {
        SocialInboundEvent event = new SocialInboundEvent(
                "max",
                channelId,
                workspaceId,
                SocialEventKind.POST_NEW,
                "dedup-max-" + remoteId,
                Map.of("mid", remoteId),
                new SocialInboundAuthor("1", false, false),
                text,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                "{\"update_type\":\"message_created\",\"message\":{\"text\":\"" + text + "\"}}",
                List.of("crosspost"));
        assertThat(crosspostHandler.handle(event, new HandlerContext(Map.of()))).isEqualTo(HandlerResult.CONTINUE);
    }

    private UUID rule(UUID source, String name, List<UUID> targets) {
        CrosspostRuleEntity rule = new CrosspostRuleEntity();
        rule.setId(UuidV7.generate());
        rule.setWorkspaceId(workspaceId);
        rule.setProjectId(projectId);
        rule.setSourceChannelId(source);
        rule.setName(name);
        rule.setStatus("active");
        rule.setConfig(Map.of("delay_minutes", 0));
        rule.setConfigVersion(1);
        rule.setCreatedBy(userId);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        ruleRepository.save(rule);
        for (UUID target : targets) {
            CrosspostRuleTargetEntity t = new CrosspostRuleTargetEntity();
            t.setId(UuidV7.generate());
            t.setRuleId(rule.getId());
            t.setTargetChannelId(target);
            t.setOptions(Map.of());
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            targetRepository.save(t);
        }
        return rule.getId();
    }

    private UUID channel(String network, String externalId, String type, String title) {
        UUID id = UuidV7.generate();
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
        return id;
    }

    private void seedWorkspace() {
        UUID planId = jdbc.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);
        jdbc.update(
                "INSERT INTO users (id, email, name) VALUES (?, ?, ?)", userId, "xp-pair-" + userId + "@ex.com", "XP");
        jdbc.update(
                """
        INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status)
        VALUES (?, 'XP', ?, ?, 'trial')
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
    }

    private String externalId(UUID channelId) {
        return jdbc.queryForObject("SELECT external_id FROM channels WHERE id = ?", String.class, channelId);
    }

    private int countPubs(UUID channelId) {
        return jdbc.queryForObject("SELECT count(*) FROM publications WHERE channel_id = ?", Integer.class, channelId);
    }

    private long countEmulatorPosts(String network) throws Exception {
        return objectMapper
                .readTree(emulator.get("/_control/posts/count"))
                .path(network)
                .asLong();
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
