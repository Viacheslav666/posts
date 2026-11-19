package ru.posts.api.qa14;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.SuggestBulkJobEntity;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.SuggestBulkJobRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.suggest.bulk.SuggestBulkProcessor;

/** TC-SUG-IDOR: approve_ids / reject_ids must ignore foreign suggest item ids. */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("ci")
class Qa14SuggestBulkIdorIT {

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
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SuggestSettingsRepository settingsRepository;

    @Autowired
    SuggestItemRepository itemRepository;

    @Autowired
    SuggestBulkJobRepository bulkJobRepository;

    @Autowired
    SuggestBulkProcessor bulkProcessor;

    @Autowired
    ChannelRepository channelRepository;

    UUID workspaceId;
    UUID userId;
    UUID settingsId;
    UUID channelId;

    @BeforeEach
    void seed() {
        userId = UuidV7.generate();
        workspaceId = UuidV7.generate();
        settingsId = UuidV7.generate();
        channelId = UuidV7.generate();
        UUID planId = jdbc.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);
        jdbc.update(
                "INSERT INTO users (id, email, name) VALUES (?, ?, ?)",
                userId,
                "bulk-idor-" + userId + "@ex.com",
                "BI");
        jdbc.update(
                """
                INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status)
                VALUES (?, 'BI', ?, ?, 'trial')
                """,
                workspaceId,
                userId,
                planId);
        ChannelEntity channel = new ChannelEntity();
        channel.setId(channelId);
        channel.setWorkspaceId(workspaceId);
        channel.setNetwork("vk");
        channel.setExternalId("-1003701");
        channel.setType("group");
        channel.setTitle("VK");
        channel.setStatus("active");
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
        SuggestSettingsEntity settings = new SuggestSettingsEntity();
        settings.setId(settingsId);
        settings.setWorkspaceId(workspaceId);
        settings.setChannelId(channelId);
        settings.setMode("auto");
        settings.setStatus("active");
        settings.setConfig(Map.of());
        settings.setConfigVersion(1);
        settings.setCreatedBy(userId);
        settings.setCreatedAt(Instant.now());
        settings.setUpdatedAt(Instant.now());
        settingsRepository.save(settings);
    }

    @Test
    @DisplayName("reject_ids with foreign item id processes only owned pending item")
    void rejectIdsIgnoresForeignSuggestItems() {
        UUID ownId = insertItem(settingsId, workspaceId, "501", "pending_moderation");
        UUID foreignChannelId = UuidV7.generate();
        ChannelEntity foreignChannel = new ChannelEntity();
        foreignChannel.setId(foreignChannelId);
        foreignChannel.setWorkspaceId(workspaceId);
        foreignChannel.setNetwork("vk");
        foreignChannel.setExternalId("-1003702");
        foreignChannel.setType("group");
        foreignChannel.setTitle("VK foreign");
        foreignChannel.setStatus("active");
        foreignChannel.setCreatedAt(Instant.now());
        foreignChannel.setUpdatedAt(Instant.now());
        channelRepository.save(foreignChannel);

        UUID foreignSettings = UuidV7.generate();
        SuggestSettingsEntity foreignCfg = new SuggestSettingsEntity();
        foreignCfg.setId(foreignSettings);
        foreignCfg.setWorkspaceId(workspaceId);
        foreignCfg.setChannelId(foreignChannelId);
        foreignCfg.setMode("auto");
        foreignCfg.setStatus("active");
        foreignCfg.setConfig(Map.of());
        foreignCfg.setConfigVersion(1);
        foreignCfg.setCreatedBy(userId);
        foreignCfg.setCreatedAt(Instant.now());
        foreignCfg.setUpdatedAt(Instant.now());
        settingsRepository.save(foreignCfg);
        UUID foreignId = insertItem(foreignSettings, workspaceId, "502", "pending_moderation");

        SuggestBulkJobEntity job = new SuggestBulkJobEntity();
        job.setId(UuidV7.generate());
        job.setWorkspaceId(workspaceId);
        job.setSettingsId(settingsId);
        job.setChannelId(channelId);
        job.setAction("reject_ids");
        job.setStatus("pending");
        job.setItemIds(new UUID[] {foreignId, ownId});
        job.setCreatedBy(userId);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        bulkJobRepository.save(job);

        SuggestBulkJobEntity done = bulkProcessor.process(job.getId());
        assertThat(done.getTotal()).isEqualTo(1);
        assertThat(done.getSucceeded()).isEqualTo(1);
        assertThat(itemRepository.findById(ownId).orElseThrow().getStatus()).isEqualTo("rejected");
        assertThat(itemRepository.findById(foreignId).orElseThrow().getStatus()).isEqualTo("pending_moderation");
    }

    @Test
    @DisplayName("approve_ids ignores item from another workspace")
    void approveIdsIgnoresOtherWorkspace() {
        UUID otherWs = UuidV7.generate();
        UUID planId = jdbc.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);
        jdbc.update(
                """
                INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status)
                VALUES (?, 'other', ?, ?, 'trial')
                """,
                otherWs,
                userId,
                planId);
        UUID ownId = insertItem(settingsId, workspaceId, "601", "pending_moderation");
        UUID foreignId = insertItem(settingsId, otherWs, "602", "pending_moderation");

        SuggestBulkJobEntity job = new SuggestBulkJobEntity();
        job.setId(UuidV7.generate());
        job.setWorkspaceId(workspaceId);
        job.setSettingsId(settingsId);
        job.setChannelId(channelId);
        job.setAction("approve_ids");
        job.setStatus("pending");
        job.setItemIds(new UUID[] {foreignId, ownId});
        job.setCreatedBy(userId);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        bulkJobRepository.save(job);

        SuggestBulkJobEntity done = bulkProcessor.process(job.getId());
        assertThat(done.getTotal()).isEqualTo(1);
        assertThat(done.getSucceeded()).isEqualTo(1);
    }

    private UUID insertItem(UUID settings, UUID ws, String vkPostId, String status) {
        SuggestItemEntity item = new SuggestItemEntity();
        item.setId(UuidV7.generate());
        item.setWorkspaceId(ws);
        item.setSettingsId(settings);
        item.setChannelId(channelId);
        item.setVkPostId(vkPostId);
        item.setStatus(status);
        item.setText("body");
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return itemRepository.save(item).getId();
    }
}
