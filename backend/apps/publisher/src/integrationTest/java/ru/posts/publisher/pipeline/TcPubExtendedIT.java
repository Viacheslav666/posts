package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.jdbc.PublicationDispatchRow;
import ru.posts.persistence.jdbc.PublicationDispatcherJdbc;
import ru.posts.ratelimit.RateLimitDecision;
import ru.posts.ratelimit.SocialRateLimiter;

/**
 * QA-5: remaining TC-PUB cases implementable on the BE-4 Telegram prototype (CI fast suite).
 */
@Tag("ci")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TcPubExtendedIT extends AbstractPipelineIT {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PublicationDispatcherJdbc dispatcherJdbc;

    @Test
    @Order(1)
    @DisplayName("TC-PUB success: happy-path publish with emulator ↔ DB reconciliation")
    void tcPubSuccessHappyPath() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002010");
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(2),
                "tc-pub-ok");

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        assertEmulatorPosts(1);
    }

    @Test
    @Order(2)
    @DisplayName("TC-PUB-04: 429 + retry_after — backoff ≥ retry_after; neighbour channel still publishes")
    void tcPub04RateLimitRetryAfter() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID limitedChannel = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002041");
        UUID neighbourChannel = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002042");

        UUID limitedPub = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                limitedChannel,
                Instant.now().minusSeconds(5),
                "tc-pub-04-limited");

        int retryAfterSeconds = 3;
        emulator()
                .putScenario(
                        """
                        {"networks":{"telegram":{"forcedError":"429","retryAfterSeconds":%d}}}
                        """
                                .formatted(retryAfterSeconds));

        Instant before = Instant.now();
        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, limitedPub))
                .isEqualTo("failed_retryable"));

        Instant nextAttempt = jdbcTemplate
                .queryForObject("SELECT next_attempt_at FROM publications WHERE id = ?", Timestamp.class, limitedPub)
                .toInstant();
        assertThat(Duration.between(before, nextAttempt).toSeconds())
                .as("next_attempt_at must respect retry_after")
                .isGreaterThanOrEqualTo(retryAfterSeconds);

        // Keep the limited publication waiting while a neighbour channel publishes.
        jdbcTemplate.update(
                "UPDATE publications SET next_attempt_at = now() + interval '1 hour' WHERE id = ?", limitedPub);
        emulator().putScenario("{\"networks\":{\"telegram\":{}}}");
        UUID neighbourPub = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                neighbourChannel,
                Instant.now().minusSeconds(1),
                "tc-pub-04-neighbour");
        assertThat(dispatchService.dispatchDueBatch()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, neighbourPub))
                .isEqualTo("succeeded"));
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM publications WHERE id = ?", String.class, limitedPub))
                .isEqualTo("failed_retryable");

        jdbcTemplate.update(
                "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?", limitedPub);
        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, limitedPub))
                .isEqualTo("succeeded"));

        assertEmulatorPosts(2);
    }

    @Test
    @Order(3)
    @DisplayName("TC-PUB hang/timeout: hang fault → retryable, then success without duplicate")
    void tcPubTimeoutThenSuccess() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002011");
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(5),
                "tc-pub-hang");

        // hangMs > telegram read-timeout (5s) so the client aborts without creating a post.
        emulator()
                .putScenario(
                        """
                        {"networks":{"telegram":{"forcedError":"hang","hangMs":6000}}}
                        """);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("failed_retryable"));

        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isZero();

        // Wait for the emulator hang thread to finish, then hard-reset scenario.
        Thread.sleep(2000);
        emulator().reset();
        socialCircuitBreaker.reset();
        jdbcTemplate.update(
                "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?", publicationId);
        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        assertEmulatorPosts(1);
    }

    @Test
    @Order(4)
    @DisplayName("TC-PUB-05: permanent 403 → failed_permanent, no infinite retries, 0 posts")
    void tcPub05PermanentFailure() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002005");
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(5),
                "tc-pub-05");

        emulator()
                .putScenario(
                        """
                        {"networks":{"telegram":{"forcedError":"permanent","httpStatus":403}}}
                        """);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("failed_permanent"));

        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT attempts FROM publications WHERE id = ?", Integer.class, publicationId);
        assertThat(attempts).isEqualTo(1);

        // Still due-looking timestamps must not re-dispatch permanent failures.
        jdbcTemplate.update(
                "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?", publicationId);
        assertThat(dispatchService.dispatchDueBatch()).isZero();

        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM posts WHERE id = (SELECT post_id FROM publications WHERE id = ?)",
                        Integer.class,
                        publicationId))
                .isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("TC-PUB-08: concurrent SKIP LOCKED claim — no double dispatch of same publication")
    void tcPub08DualSchedulerNoDoubleDispatch() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002008");
        // All due well before look-ahead horizon (claimDue uses now + lookAhead).
        Instant base = Instant.now().minusSeconds(120);
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            UUID id = PipelineTestSupport.insertPublication(
                    jdbcTemplate,
                    workspaceId,
                    UuidV7.generate(),
                    UuidV7.generate(),
                    channelId,
                    base.plusMillis(i),
                    "tc-pub-08-" + i);
            ids.add(id);
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<List<PublicationDispatchRow>> task =
                    () -> dispatcherJdbc.claimDue(20, Duration.ofMinutes(10), Duration.ofSeconds(5));
            Future<List<PublicationDispatchRow>> first = pool.submit(task);
            Future<List<PublicationDispatchRow>> second = pool.submit(task);
            List<PublicationDispatchRow> a = first.get(30, TimeUnit.SECONDS);
            List<PublicationDispatchRow> b = second.get(30, TimeUnit.SECONDS);

            assertThat(a.size() + b.size()).isEqualTo(40);
            Set<UUID> claimed = new HashSet<>();
            a.forEach(row -> claimed.add(row.id()));
            b.forEach(row -> assertThat(claimed.add(row.id())).isTrue());
            assertThat(claimed).isEqualTo(ids);

            Integer queued = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM publications WHERE state = 'queued'", Integer.class);
            assertThat(queued).isEqualTo(40);
        } finally {
            pool.shutdownNow();
        }

        // Revert only this channel's rows and publish once — 0 duplicates in emulator.
        jdbcTemplate.update(
                """
                UPDATE publications
                SET state = 'pending', locked_until = NULL, locked_by = NULL, next_attempt_at = NULL
                WHERE channel_id = ?
                """,
                channelId);
        int idle = 0;
        while (idle < 40) {
            int n = dispatchService.dispatchDueBatch();
            Long left = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM publications
                    WHERE channel_id = ?
                      AND state IN ('pending','failed_retryable','queued','in_progress')
                    """,
                    Long.class,
                    channelId);
            if (left != null && left == 0) {
                break;
            }
            if (n == 0) {
                idle++;
                Thread.sleep(50);
            } else {
                idle = 0;
            }
        }
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            Long pending = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM publications
                    WHERE channel_id = ? AND state <> 'succeeded'
                    """,
                    Long.class,
                    channelId);
            assertThat(pending).isZero();
        });
        assertEmulatorPosts(40);
    }

    @Test
    @Order(6)
    @DisplayName("Deferred publication (run_at in future) is not dispatched")
    void deferredPublicationNotDispatched() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002090");
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().plus(Duration.ofHours(2)),
                "tc-pub-deferred");

        assertThat(dispatchService.dispatchDueBatch()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("pending");
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isZero();
    }

    @Test
    @Order(7)
    @DisplayName("Cancelled publication is not published")
    void cancelledPublicationSkipped() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002091");
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(5),
                "tc-pub-cancel");
        jdbcTemplate.update("UPDATE publications SET state = 'cancelled' WHERE id = ?", publicationId);

        assertThat(dispatchService.dispatchDueBatch()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("cancelled");
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isZero();
    }

    @Test
    @Order(8)
    @DisplayName("Bucket4j/rate-limit deny → rate_limited then success without duplicates")
    void rateLimitDenyThenSuccess() throws Exception {
        SocialRateLimiter original =
                (SocialRateLimiter) ReflectionTestUtils.getField(publicationProcessor, "rateLimiter");
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        SocialRateLimiter stub = (network, accountId, chatId) -> {
            if (calls.getAndIncrement() == 0) {
                return RateLimitDecision.deny(Duration.ofMillis(50));
            }
            return RateLimitDecision.allow();
        };
        ReflectionTestUtils.setField(publicationProcessor, "rateLimiter", stub);
        try {
            UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
            UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002070");
            UUID publicationId = PipelineTestSupport.insertPublication(
                    jdbcTemplate,
                    workspaceId,
                    UuidV7.generate(),
                    UuidV7.generate(),
                    channelId,
                    Instant.now().minusSeconds(2),
                    "tc-pub-rl");

            dispatchService.dispatchDueBatch();
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                        .isEqualTo("failed_retryable");
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT error_code FROM publications WHERE id = ?", String.class, publicationId))
                        .isEqualTo("rate_limited");
            });

            jdbcTemplate.update(
                    "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?",
                    publicationId);
            dispatchService.dispatchDueBatch();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                    .isEqualTo("succeeded"));
            assertEmulatorPosts(1);
        } finally {
            ReflectionTestUtils.setField(publicationProcessor, "rateLimiter", original);
        }
    }

    @Test
    @Order(9)
    @DisplayName("Circuit breaker open → failed_retryable, then recovers after reset")
    void circuitBreakerOpenThenRecover() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002080");
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(5),
                "tc-pub-cb");

        socialCircuitBreaker.transitionToOpenState();
        assertThat(socialCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("failed_retryable"));

        assertThat(objectMapper
                        .readTree(emulator().get("/_control/posts/count"))
                        .path("telegram")
                        .asLong())
                .isZero();

        socialCircuitBreaker.reset();
        jdbcTemplate.update(
                "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?", publicationId);
        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));
        assertEmulatorPosts(1);
    }

    @Test
    @Order(10)
    @DisplayName("TC-PUB-08 reaper after scheduler restart: stuck queued → republished once")
    void schedulerRestartReaperNoLossNoDuplicate() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1002088");
        UUID publicationId = PipelineTestSupport.insertPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(5),
                "tc-pub-08-reaper");

        jdbcTemplate.update(
                """
                UPDATE publications
                SET state = 'queued', locked_by = 'scheduler-dead',
                    locked_until = now() - interval '2 minutes'
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
        assertEmulatorPosts(1);
    }

    /** Emulator is reset in {@code @BeforeEach}; count must match this test's sends. */
    private void assertEmulatorPosts(long expected) throws Exception {
        long emulatorCount = objectMapper
                .readTree(emulator().get("/_control/posts/count"))
                .path("telegram")
                .asLong();
        assertThat(emulatorCount)
                .as("emulator sends (0 losses / 0 duplicates for this scenario)")
                .isEqualTo(expected);
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/verify"))
                        .path("ok")
                        .asBoolean())
                .isTrue();
    }
}
