package ru.posts.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import ru.posts.persistence.jdbc.PublicationDispatchRow;
import ru.posts.persistence.jdbc.PublicationDispatcherJdbc;

@SpringBootTest(classes = PersistenceIntegrationTestApp.class)
@Testcontainers
class PublicationDispatcherJdbcIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Autowired
    private PublicationDispatcherJdbc dispatcherJdbc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void claimDueUsesSkipLockedAndDoesNotDuplicate() throws Exception {
        UUID workspaceId = seedWorkspace();
        UUID postId = UuidV7.generate();
        UUID channelId = seedChannel(workspaceId);
        UUID variantId = UuidV7.generate();

        Instant base = Instant.now().minusSeconds(30);
        for (int i = 0; i < 20; i++) {
            insertPublication(workspaceId, postId, variantId, channelId, base.plusSeconds(i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<List<PublicationDispatchRow>> task =
                    () -> dispatcherJdbc.claimDue(10, Duration.ofMinutes(10), Duration.ofSeconds(5));
            Future<List<PublicationDispatchRow>> first = pool.submit(task);
            Future<List<PublicationDispatchRow>> second = pool.submit(task);
            List<PublicationDispatchRow> a = first.get(30, TimeUnit.SECONDS);
            List<PublicationDispatchRow> b = second.get(30, TimeUnit.SECONDS);

            assertThat(a).isNotEmpty();
            assertThat(b).isNotEmpty();
            assertThat(a.size() + b.size()).isEqualTo(20);
            assertThat(a.stream().map(PublicationDispatchRow::id).toList())
                    .doesNotContainAnyElementsOf(
                            b.stream().map(PublicationDispatchRow::id).toList());

            Integer queued = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM publications WHERE state = 'queued'", Integer.class);
            assertThat(queued).isEqualTo(20);
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID seedWorkspace() {
        UUID userId = UuidV7.generate();
        UUID workspaceId = UuidV7.generate();
        UUID planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE code = 'free'", UUID.class);

        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, name)
                VALUES (?, ?, ?)
                """,
                userId,
                "dispatch-" + userId + "@example.com",
                "Dispatcher");
        jdbcTemplate.update(
                """
                INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status)
                VALUES (?, ?, ?, ?, 'trial')
                """,
                workspaceId,
                "Dispatch WS",
                userId,
                planId);
        return workspaceId;
    }

    private UUID seedChannel(UUID workspaceId) {
        UUID channelId = UuidV7.generate();
        jdbcTemplate.update(
                """
                INSERT INTO channels (id, workspace_id, network, external_id, type, title)
                VALUES (?, ?, 'tg', ?, 'channel', 'Dispatch')
                """,
                channelId,
                workspaceId,
                channelId.toString());
        return channelId;
    }

    private void insertPublication(UUID workspaceId, UUID postId, UUID variantId, UUID channelId, Instant runAt)
            throws Exception {
        UUID id = UuidV7.generate();
        // posts/variants FKs are not enforced on publications (partitioned hot path); still seed post for realism
        ensurePost(workspaceId, postId, channelId, variantId);
        String key = sha256(variantId + "|" + runAt + "|" + id);
        jdbcTemplate.update(
                """
                INSERT INTO publications (
                    id, workspace_id, post_id, variant_id, channel_id, network,
                    run_at, idempotency_key, state)
                VALUES (?, ?, ?, ?, ?, 'tg', ?, ?, 'pending')
                """,
                id,
                workspaceId,
                postId,
                variantId,
                channelId,
                Timestamp.from(runAt),
                key);
    }

    private void ensurePost(UUID workspaceId, UUID postId, UUID channelId, UUID variantId) {
        Integer posts = jdbcTemplate.queryForObject("SELECT count(*) FROM posts WHERE id = ?", Integer.class, postId);
        if (posts != null && posts > 0) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO posts (id, workspace_id, title, base_content, status)
                VALUES (?, ?, 'dispatch', '{}'::jsonb, 'scheduled')
                """,
                postId,
                workspaceId);
        jdbcTemplate.update(
                """
                INSERT INTO post_variants (id, post_id, channel_id, network, content, options)
                VALUES (?, ?, ?, 'tg', '{}'::jsonb, '{}'::jsonb)
                """,
                variantId,
                postId,
                channelId);
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
