package ru.posts.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.jdbc.PublicationPartitions;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;
import ru.posts.persistence.jdbc.SocialRemoteActionLifecycleJdbc;
import ru.posts.persistence.repository.SuggestItemRepository;

@SpringBootTest(classes = PersistenceIntegrationTestApp.class)
@Testcontainers
class P2ReviewFixesIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SocialRemoteActionLifecycleJdbc lifecycleJdbc;

    @Autowired
    private SuggestItemRepository suggestItemRepository;

    @Autowired
    private RemotePostRegistryJdbc registryJdbc;

    @Test
    void sameIdempotencyKeyWithDifferentRunAtInsertsOnce() throws Exception {
        String key = "suggest:" + UuidV7.generate() + ":race";
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<UUID>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Instant runAt = Instant.now().plusSeconds(i * 60L);
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return lifecycleJdbc.enqueue(
                            "delete_post",
                            UuidV7.generate(),
                            UuidV7.generate(),
                            "{}",
                            "{}",
                            key,
                            runAt,
                            "suggest",
                            "{}",
                            null);
                }));
            }
            start.countDown();
            UUID first = futures.getFirst().get(30, TimeUnit.SECONDS);
            for (Future<UUID> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(first);
            }
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM social_remote_actions WHERE idempotency_key = ?", Integer.class, key);
            assertThat(count).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void reconcilerSeesDeleteWithoutRemoteActionIdAndSkipsAFuturePendingPublish() {
        Seed seed = seed();
        UUID dueId = UuidV7.generate();
        UUID futureId = UuidV7.generate();
        insertItem(seed, dueId, "del", "rejected");
        insertItem(seed, futureId, "fut", "scheduled");
        Instant now = Instant.now();
        lifecycleJdbc.enqueue(
                "delete_post",
                seed.workspaceId,
                seed.channelId,
                "{}",
                "{}",
                "suggest-delete:" + seed.channelId + ":del",
                now,
                "suggest",
                "{}",
                seed.userId);
        UUID futureAction = lifecycleJdbc.enqueue(
                "publish_suggested",
                seed.workspaceId,
                seed.channelId,
                "{}",
                "{}",
                "suggest:" + seed.channelId + ":fut",
                now,
                "suggest",
                "{}",
                seed.userId);
        jdbcTemplate.update(
                "UPDATE social_remote_actions SET next_attempt_at = ? WHERE id = ?",
                java.sql.Timestamp.from(now.plus(2, ChronoUnit.DAYS)),
                futureAction);

        List<UUID> due = suggestItemRepository.findDueForRemoteReconcile(50).stream()
                .map(SuggestItemEntity::getId)
                .toList();
        assertThat(due).contains(dueId);
        assertThat(due).doesNotContain(futureId);
    }

    @Test
    void ownFingerprintMatchesOnTheTextPartWhenMediaDiffers() {
        Seed seed = seed();
        String stored = "sim:abc123|media:one";
        String incoming = "sim:abc123|media:two";
        assertThat(stored).isNotEqualTo(incoming);
        jdbcTemplate.update(
                """
                INSERT INTO remote_post_registry (
                    id, network, channel_id, remote_post_id, origin, root_origin, hop,
                    content_fingerprint, workspace_id)
                VALUES (?, 'vk', ?, 'post-1', 'publication', '{}'::jsonb, 0, ?, ?)
                """,
                UuidV7.generate(),
                seed.channelId,
                stored,
                seed.workspaceId);
        assertThat(registryJdbc.isOwnByFingerprint(
                        seed.channelId, incoming, Instant.now().minusSeconds(60)))
                .isTrue();
    }

    @Test
    void publicationPartitionPast2027IsCreated() {
        PublicationPartitions.ensureMonth(jdbcTemplate, "publications", Instant.parse("2028-03-15T00:00:00Z"));
        Integer found = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_class WHERE relname = 'publications_2028_03'", Integer.class);
        assertThat(found).isEqualTo(1);
    }

    private Seed seed() {
        UUID userId = UuidV7.generate();
        UUID workspaceId = UuidV7.generate();
        UUID projectId = UuidV7.generate();
        UUID channelId = UuidV7.generate();
        UUID settingsId = UuidV7.generate();
        UUID planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);
        jdbcTemplate.update(
                "INSERT INTO users (id, email, name) VALUES (?, ?, ?)", userId, "p2-" + userId + "@example.com", "P2");
        jdbcTemplate.update(
                "INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status) VALUES (?, ?, ?, ?, 'trial')",
                workspaceId,
                "P2",
                userId,
                planId);
        jdbcTemplate.update(
                "INSERT INTO projects (id, workspace_id, name) VALUES (?, ?, ?)", projectId, workspaceId, "P2");
        jdbcTemplate.update(
                "INSERT INTO channels (id, workspace_id, network, external_id, type, title) VALUES (?, ?, 'vk', ?, 'group', 'P2')",
                channelId,
                workspaceId,
                channelId.toString());
        jdbcTemplate.update(
                """
                INSERT INTO suggest_settings (
                    id, workspace_id, project_id, channel_id, status, mode, created_by)
                VALUES (?, ?, ?, ?, 'active', 'auto', ?)
                """,
                settingsId,
                workspaceId,
                projectId,
                channelId,
                userId);
        return new Seed(userId, workspaceId, channelId, settingsId);
    }

    private void insertItem(Seed seed, UUID id, String vkPostId, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO suggest_items (
                    id, workspace_id, channel_id, settings_id, vk_post_id, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                seed.workspaceId,
                seed.channelId,
                seed.settingsId,
                vkPostId,
                status);
    }

    private record Seed(UUID userId, UUID workspaceId, UUID channelId, UUID settingsId) {}
}
