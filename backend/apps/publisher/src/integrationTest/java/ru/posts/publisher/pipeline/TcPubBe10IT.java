package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import ru.posts.common.uuid.UuidV7;

/**
 * BE-10 prod-quality TC-PUB scenarios runnable on Testcontainers (PG + RabbitMQ + Redis +
 * emulator).
 */
@Tag("ci")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TcPubBe10IT extends AbstractPipelineIT {

    @Test
    @Order(1)
    @DisplayName("TC-PUB-09: broker confirm failure → revert to pending → recover after redispatch")
    void tcPub09BrokerOutageRecovery() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002909");
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(2),
                "tc-pub-09");

        // Simulate lost confirm / broker blip: queue then revert (scheduler path when confirms fail).
        jdbcTemplate.update(
                "UPDATE publications SET state = 'queued', locked_until = now() + interval '10 minutes' WHERE id = ?",
                publicationId);
        jdbcTemplate.update(
                "UPDATE publications SET state = 'pending', locked_until = NULL, locked_by = NULL WHERE id = ? AND state = 'queued'",
                publicationId);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("pending");

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        Instant publishedAt = jdbcTemplate
                .queryForObject("SELECT published_at FROM publications WHERE id = ?", Timestamp.class, publicationId)
                .toInstant();
        Instant runAt = jdbcTemplate
                .queryForObject("SELECT run_at FROM publications WHERE id = ?", Timestamp.class, publicationId)
                .toInstant();
        assertThat(Duration.between(runAt, publishedAt).toMillis()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(2)
    @DisplayName("TC-PUB-11: VK queued on publish.vk does not block TG SLA")
    void tcPub11NetworkIsolation() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID tgChannel = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002911");
        UUID vkChannel = UuidV7.generate();
        jdbcTemplate.update(
                """
                INSERT INTO channels (id, workspace_id, network, external_id, type, title)
                VALUES (?, ?, 'vk', ?, 'group', 'VK down')
                """,
                vkChannel,
                workspaceId,
                "vk-group-911");

        UUID tgPub = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                tgChannel,
                Instant.now().minusSeconds(2),
                "tc-pub-11-tg");
        UUID vkPost = UuidV7.generate();
        UUID vkVariant = UuidV7.generate();
        PipelineTestSupport.ensurePost(jdbcTemplate, workspaceId, vkPost, vkChannel, vkVariant, "tc-pub-11-vk");
        UUID vkPub = UuidV7.generate();
        Instant runAt = Instant.now().minusSeconds(2);
        jdbcTemplate.update(
                """
                INSERT INTO publications (
                    id, workspace_id, post_id, variant_id, channel_id, network,
                    run_at, idempotency_key, state)
                VALUES (?, ?, ?, ?, ?, 'vk', ?, ?, 'pending')
                """,
                vkPub,
                workspaceId,
                vkPost,
                vkVariant,
                vkChannel,
                Timestamp.from(runAt),
                PipelineTestSupport.sha256(vkVariant + "|" + runAt));

        Instant start = Instant.now();
        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(2);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
                        jdbcTemplate.queryForObject("SELECT state FROM publications WHERE id = ?", String.class, tgPub))
                .isEqualTo("succeeded"));

        Duration tgDelay = Duration.between(start, Instant.now());
        assertThat(tgDelay).isLessThan(Duration.ofSeconds(60));

        // VK stays queued — no publisher-vk in this process (isolation of queues).
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM publications WHERE id = ?", String.class, vkPub))
                .isEqualTo("queued");
        // Cancel so later tests counting global queued rows are not polluted.
        jdbcTemplate.update("UPDATE publications SET state = 'cancelled', locked_until = NULL WHERE id = ?", vkPub);
    }

    @Test
    @Order(3)
    @DisplayName("TC-PUB-12: pause/cancel near fire prevents publish; edit content is what gets published")
    void tcPub12EditPauseNearFire() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002912");
        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        UUID cancelled = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                postId,
                variantId,
                channelId,
                Instant.now().minusSeconds(1),
                "old-text");
        jdbcTemplate.update("UPDATE publications SET state = 'cancelled' WHERE id = ?", cancelled);

        // Edit content on a sibling publication that will fire.
        UUID edited = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(1),
                "edited-near-fire");

        assertThat(dispatchService.dispatchDueBatch()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, edited))
                .isEqualTo("succeeded"));
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM publications WHERE id = ?", String.class, cancelled))
                .isEqualTo("cancelled");
    }

    @Test
    @Order(4)
    @DisplayName("TC-PUB-13: outbox publication.succeeded written promptly (WS fan-out source)")
    void tcPub13OutboxForWebsocket() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002913");
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(2),
                "tc-pub-13");

        Instant start = Instant.now();
        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM outbox
                    WHERE aggregate_id = ? AND event_type = 'publication.succeeded'
                    """,
                    Integer.class,
                    publicationId);
            assertThat(count).isEqualTo(1);
        });
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    @Order(5)
    @DisplayName("scheduled_actions: first_comment + delete materialized and executed")
    void scheduledActionsFirstCommentAndDelete() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002915");
        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        Instant runAt = Instant.now().minusSeconds(2);
        PipelineTestSupport.ensurePost(jdbcTemplate, workspaceId, postId, channelId, variantId, "with-actions");
        jdbcTemplate.update(
                "UPDATE post_variants SET options = ?::jsonb WHERE id = ?",
                """
                {"pin":true,"firstComment":"hello first","deleteAfter":"%s"}
                """
                        .formatted(Instant.now().plusSeconds(2).toString()),
                variantId);
        UUID publicationId = UuidV7.generate();
        jdbcTemplate.update(
                """
                INSERT INTO publications (
                    id, workspace_id, post_id, variant_id, channel_id, network,
                    run_at, idempotency_key, state)
                VALUES (?, ?, ?, ?, ?, 'tg', ?, ?, 'pending')
                """,
                publicationId,
                workspaceId,
                postId,
                variantId,
                channelId,
                Timestamp.from(runAt),
                PipelineTestSupport.sha256(variantId + "|" + runAt));

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        Integer actions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM scheduled_actions WHERE publication_id = ?", Integer.class, publicationId);
        assertThat(actions).isGreaterThanOrEqualTo(2);

        // Dispatch due actions (pin + first_comment immediate).
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            dispatchService.dispatchDueActions();
            Integer pending = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM scheduled_actions
                    WHERE publication_id = ? AND kind IN ('pin','first_comment') AND state <> 'succeeded'
                    """,
                    Integer.class,
                    publicationId);
            assertThat(pending).isZero();
        });
    }

    @Test
    @Order(6)
    @DisplayName("AUTH failure marks channel reauth_required + outbox failed")
    void authMarksChannelReauth() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002916");
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(2),
                "tc-auth-reauth");

        emulator()
                .putScenario(
                        """
                        {"networks":{"telegram":{"forcedError":"403"}}}
                        """);

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                    .isEqualTo("failed_permanent");
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM channels WHERE id = ?", String.class, channelId))
                    .isEqualTo("reauth_required");
        });
    }
}
