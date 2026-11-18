package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import ru.posts.common.uuid.UuidV7;

/**
 * QA-10: TC-AD publisher regression — fail-closed without erid, ORD auto-delete, 0 duplicates on
 * ad retry (TC-PUB parity for advertising posts).
 */
@Tag("ci")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrdAdPipelineIT extends AbstractPipelineIT {

    private static final String ERID = "test-erid-pipeline-0001";
    private static final String ADVERTISER = "ООО Рекламодатель";
    private static final String INN = "7707083893";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("TC-AD-12: ad publication without erid never reaches network (fail-closed)")
    void tcAd12FailClosedWithoutErid() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1009101");
        UUID publicationId = PipelineTestSupport.insertAdPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(2),
                "ad-no-erid",
                null,
                ADVERTISER,
                INN,
                30,
                true);

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("failed_permanent"));

        assertEmulatorTelegramPosts(0);
        JsonNode verify = objectMapper.readTree(emulator().get("/_control/verify"));
        assertThat(verify.path("ok").asBoolean()).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("TC-AD-07: ad post retries then succeeds — 0 duplicates, marker present")
    void tcAd07RetryNoDuplicate() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1009102");
        UUID postId = UuidV7.generate();
        UUID variantId = UuidV7.generate();
        UUID publicationId = PipelineTestSupport.insertAdPublication(
                jdbcTemplate,
                workspaceId,
                postId,
                variantId,
                channelId,
                Instant.now().minusSeconds(2),
                "ad-retry-body",
                ERID,
                ADVERTISER,
                INN,
                30,
                false);

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

        emulator().putScenario("{\"networks\":{\"telegram\":{}}}");
        jdbcTemplate.update(
                "UPDATE publications SET next_attempt_at = now() - interval '1 second' WHERE id = ?", publicationId);

        dispatchService.dispatchDueBatch();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        assertEmulatorTelegramPosts(1);
        JsonNode posts = objectMapper.readTree(emulator().get("/_control/posts?network=telegram"));
        assertThat(posts.isArray()).isTrue();
        assertThat(posts).hasSize(1);
        String text = posts.get(0).path("text").asText();
        assertThat(text).contains("Реклама");
        assertThat(text).contains("erid: " + ERID);
        assertThat(text).contains("ad-retry-body");

        JsonNode verify = objectMapper.readTree(emulator().get("/_control/verify"));
        assertThat(verify.path("ok").asBoolean()).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("TC-AD-06: ORD display period materializes delete action; re-dispatch is idempotent")
    void tcAd06AutoDeleteAfterPeriod() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1009103");
        UUID publicationId = PipelineTestSupport.insertAdPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(2),
                "ad-autodelete",
                ERID,
                ADVERTISER,
                INN,
                1,
                true);

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        Integer deleteActions = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM scheduled_actions
                WHERE publication_id = ? AND kind = 'delete'
                """,
                Integer.class,
                publicationId);
        assertThat(deleteActions).isEqualTo(1);

        jdbcTemplate.update(
                """
                UPDATE scheduled_actions
                SET run_at = now() - interval '1 second'
                WHERE publication_id = ? AND kind = 'delete'
                """,
                publicationId);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            dispatchService.dispatchDueActions();
            assertThat(jdbcTemplate.queryForObject(
                            """
                            SELECT state FROM scheduled_actions
                            WHERE publication_id = ? AND kind = 'delete'
                            """,
                            String.class,
                            publicationId))
                    .isEqualTo("succeeded");
        });

        // Idempotent re-delete: force another attempt on already-succeeded action should stay success
        // (ActionProcessor treats already-deleted remote as success; re-dispatch of due batch finds none).
        Integer pendingDeletes = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM scheduled_actions
                WHERE publication_id = ? AND kind = 'delete' AND state <> 'succeeded'
                """,
                Integer.class,
                publicationId);
        assertThat(pendingDeletes).isZero();
        assertThat(dispatchService.dispatchDueActions()).isZero();
        assertEmulatorTelegramPosts(0);
    }

    @Test
    @Order(4)
    @DisplayName("TC-AD-04/PUB: scheduled ad with erid publishes once with marker")
    void tcAdScheduledPublishWithMarker() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "-1009104");
        UUID publicationId = PipelineTestSupport.insertAdPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(1),
                "ad-deferred-ok",
                ERID,
                ADVERTISER,
                INN,
                30,
                false);

        // Future run_at must not dispatch.
        jdbcTemplate.update(
                "UPDATE publications SET run_at = now() + interval '1 hour', state = 'pending' WHERE id = ?",
                publicationId);
        assertThat(dispatchService.dispatchDueBatch()).isZero();
        assertEmulatorTelegramPosts(0);

        jdbcTemplate.update(
                "UPDATE publications SET run_at = now() - interval '1 second', state = 'pending' WHERE id = ?",
                publicationId);
        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));
        assertEmulatorTelegramPosts(1);
    }

    private void assertEmulatorTelegramPosts(long expected) throws Exception {
        long emulatorCount = objectMapper
                .readTree(emulator().get("/_control/posts/count"))
                .path("telegram")
                .asLong();
        assertThat(emulatorCount)
                .as("emulator telegram posts (0 losses / 0 duplicates for ad scenario)")
                .isEqualTo(expected);
        assertThat(objectMapper
                        .readTree(emulator().get("/_control/verify"))
                        .path("ok")
                        .asBoolean())
                .isTrue();
    }
}
