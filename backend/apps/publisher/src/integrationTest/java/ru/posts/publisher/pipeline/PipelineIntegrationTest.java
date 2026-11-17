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
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import ru.posts.common.uuid.UuidV7;

/**
 * BE-4 / QA-5: core TC-PUB cases that run in every CI integrationTest.
 * Heavy load lives in {@link PipelineLoadIT} ({@code @Tag("nightly")}).
 */
@Tag("ci")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineIntegrationTest extends AbstractPipelineIT {

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
}
