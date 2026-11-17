package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.web.client.RestClient;
import ru.posts.common.uuid.UuidV7;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineIntegrationTest extends AbstractPipelineIT {

    private static final int PUBLICATION_COUNT = 10_000;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("TC-PUB-02: 5xx three times then success — one post, four attempts")
    void tcPub02RetryThenSuccess() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002002");
        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                postId,
                variantId,
                channelId,
                Instant.now().minusSeconds(5),
                "tc-pub-02");

        emulator()
                .putScenario(
                        """
                        {"networks":{"telegram":{"forcedError":"5xx","httpStatus":503}}}
                        """);

        for (int i = 0; i < 3; i++) {
            dispatchService.dispatchDueBatch();
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                    .isEqualTo("failed_retryable"));
            jdbcTemplate.update(
                    "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?",
                    publicationId);
        }

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT attempts FROM publications WHERE id = ?", Integer.class, publicationId))
                .isEqualTo(3);

        emulator().putScenario("{\"networks\":{\"telegram\":{}}}");
        jdbcTemplate.update(
                "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?", publicationId);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT attempts FROM publications WHERE id = ?", Integer.class, publicationId))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM publication_attempts WHERE publication_id = ?",
                        Integer.class,
                        publicationId))
                .isEqualTo(4);

        JsonNode verify = objectMapper.readTree(emulator().get("/_control/verify"));
        assertThat(verify.path("ok").asBoolean()).isTrue();
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("TC-PUB-03: lost success response does not create a duplicate")
    void tcPub03LostResponseNoDuplicate() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002003");
        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                postId,
                variantId,
                channelId,
                Instant.now().minusSeconds(5),
                "tc-pub-03");

        emulator()
                .putScenario(
                        """
                        {"networks":{"telegram":{"forcedError":"drop"}}}
                        """);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isIn("succeeded", "failed_retryable"));

        if (!"succeeded"
                .equals(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))) {
            emulator().putScenario("{\"networks\":{\"telegram\":{}}}");
            jdbcTemplate.update(
                    "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?",
                    publicationId);
            dispatchService.dispatchDueBatch();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                    .isEqualTo("succeeded"));
        }

        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isEqualTo(1);
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/verify"))
                        .path("ok")
                        .asBoolean())
                .isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("TC-PUB-06: duplicate Rabbit delivery publishes once")
    void tcPub06DuplicateDelivery() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002006");
        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                postId,
                variantId,
                channelId,
                Instant.now().minusSeconds(5),
                "tc-pub-06");

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        assertThat(publicationProcessor.process(publicationId)).isTrue();
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isEqualTo(1);
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/verify"))
                        .path("ok")
                        .asBoolean())
                .isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("TC-PUB-07: publisher crash mid-flight is recovered without duplicates")
    void tcPub07PublisherCrashRecovery() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002007");
        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        Instant runAt = Instant.now().minusSeconds(5);
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate, workspaceId, postId, variantId, channelId, runAt, "tc-pub-07");

        String idempotencyKey = jdbcTemplate.queryForObject(
                "SELECT idempotency_key FROM publications WHERE id = ?", String.class, publicationId);
        RestClient.create(emulator().baseUrl())
                .post()
                .uri("/bot{token}/sendMessage", "emulator-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("chat_id", "-1002007", "text", "tc-pub-07", "idempotency_key", idempotencyKey))
                .retrieve()
                .toBodilessEntity();

        jdbcTemplate.update(
                """
                UPDATE publications
                SET state = 'in_progress', attempts = 1, locked_by = 'dead-pod',
                    locked_until = now() - interval '1 minute'
                WHERE id = ?
                """,
                publicationId);

        assertThat(dispatchService.reapStuck()).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("failed_retryable");

        jdbcTemplate.update(
                "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?", publicationId);
        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isEqualTo(1);
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/verify"))
                        .path("ok")
                        .asBoolean())
                .isTrue();
    }

    @Test
    @Order(5)
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    @DisplayName("10_000 publications in one minute slot — all published, 0 duplicates, p99 < 60s")
    void tenThousandPublicationsMeetSlo() throws Exception {
        jdbcTemplate.update("DELETE FROM publication_attempts");
        jdbcTemplate.update("DELETE FROM publications");
        jdbcTemplate.update("DELETE FROM post_variants");
        jdbcTemplate.update("DELETE FROM posts");

        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-10010000");
        Instant seedRunAt = Instant.now().plus(Duration.ofMinutes(30));

        List<UUID> postIds = new ArrayList<>(PUBLICATION_COUNT);
        List<UUID> variantIds = new ArrayList<>(PUBLICATION_COUNT);
        List<UUID> publicationIds = new ArrayList<>(PUBLICATION_COUNT);
        List<String> keys = new ArrayList<>(PUBLICATION_COUNT);
        for (int i = 0; i < PUBLICATION_COUNT; i++) {
            postIds.add(UuidV7.generate());
            variantIds.add(UuidV7.generate());
            publicationIds.add(UuidV7.generate());
            keys.add(sha256(variantIds.get(i) + "|" + seedRunAt));
        }

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO posts (id, workspace_id, title, base_content, status)
                VALUES (?, ?, 'load', ?::jsonb, 'scheduled')
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setObject(1, postIds.get(i));
                        ps.setObject(2, workspaceId);
                        ps.setString(3, "{\"text\":\"load-" + i + "\"}");
                    }

                    @Override
                    public int getBatchSize() {
                        return PUBLICATION_COUNT;
                    }
                });

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO post_variants (id, post_id, channel_id, network, content, options)
                VALUES (?, ?, ?, 'tg', ?::jsonb, '{}'::jsonb)
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setObject(1, variantIds.get(i));
                        ps.setObject(2, postIds.get(i));
                        ps.setObject(3, channelId);
                        ps.setString(4, "{\"text\":\"load-" + i + "\"}");
                    }

                    @Override
                    public int getBatchSize() {
                        return PUBLICATION_COUNT;
                    }
                });

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO publications (
                    id, workspace_id, post_id, variant_id, channel_id, network,
                    run_at, idempotency_key, state)
                VALUES (?, ?, ?, ?, ?, 'tg', ?, ?, 'pending')
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setObject(1, publicationIds.get(i));
                        ps.setObject(2, workspaceId);
                        ps.setObject(3, postIds.get(i));
                        ps.setObject(4, variantIds.get(i));
                        ps.setObject(5, channelId);
                        ps.setTimestamp(6, Timestamp.from(seedRunAt));
                        ps.setString(7, keys.get(i));
                    }

                    @Override
                    public int getBatchSize() {
                        return PUBLICATION_COUNT;
                    }
                });

        Instant dueAt = Instant.now().minusSeconds(1);
        jdbcTemplate.update("UPDATE publications SET run_at = ?", Timestamp.from(dueAt));

        Instant started = Instant.now();
        int idleRounds = 0;
        while (idleRounds < 80) {
            int dispatched = dispatchService.dispatchDueBatch();
            Long remaining = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM publications
                    WHERE state IN ('pending','failed_retryable','queued','in_progress')
                    """,
                    Long.class);
            if (remaining != null && remaining == 0) {
                break;
            }
            if (dispatched == 0) {
                idleRounds++;
                Thread.sleep(50);
            } else {
                idleRounds = 0;
            }
        }

        await().atMost(Duration.ofMinutes(6)).untilAsserted(() -> {
            Long succeeded = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM publications WHERE state = 'succeeded'", Long.class);
            assertThat(succeeded).isEqualTo((long) PUBLICATION_COUNT);
        });

        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isEqualTo(PUBLICATION_COUNT);
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/verify"))
                        .path("ok")
                        .asBoolean())
                .isTrue();

        List<Long> delaysMs = jdbcTemplate.query(
                """
                SELECT EXTRACT(EPOCH FROM (published_at - run_at)) * 1000
                FROM publications
                WHERE state = 'succeeded'
                """,
                (rs, rowNum) -> rs.getLong(1));
        Collections.sort(delaysMs);
        int p99Index = Math.max(0, (int) Math.ceil(delaysMs.size() * 0.99) - 1);
        long p99 = delaysMs.get(p99Index);
        assertThat(p99)
                .as("p99 delay ms (after dueAt=%s, elapsed=%ss)", dueAt, Duration.between(started, Instant.now()))
                .isLessThan(60_000);
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
