package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;
import ru.posts.persistence.service.RemoteActionService;
import ru.posts.publisher.service.RemoteActionProcessor;

/** BE-33 acceptance: VK remote moderation / suggest / registry against social-emulator. */
@Tag("ci")
class Be33RemoteActionsVkIT extends AbstractVkPipelineIT {

    @Autowired
    private RemoteActionProcessor remoteActionProcessor;

    @Autowired
    private RemoteActionService remoteActionService;

    @Autowired
    private RemotePostRegistryJdbc remotePostRegistryJdbc;

    @Value("${posts.social.vk.base-url}")
    private String vkBaseUrl;

    @Test
    @DisplayName("live API guard: VK base-url must point at emulator, not api.vk.com")
    void vkBaseUrlPointsAtEmulator() {
        assertThat(vkBaseUrl).isNotBlank();
        assertThat(vkBaseUrl.toLowerCase(Locale.ROOT)).doesNotContain("api.vk.com");
        assertThat(vkBaseUrl).startsWith(emulator().baseUrl());
    }

    @Test
    @DisplayName("delete_comment happy path + idempotent re-enqueue")
    void deleteCommentHappyPathAndIdempotent() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        String ownerId = "-1003301";
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", ownerId, "group");

        emulator()
                .put(
                        "/_control/comments",
                        """
                        {"items":[{"network":"vk","ownerId":"%s","commentId":"91","type":"wall","text":"spam"}]}
                        """
                                .formatted(ownerId));

        String key = "be33-vk-del-cmt-" + UuidV7.generate();
        String target = "{\"commentType\":\"wall\",\"remoteCommentId\":\"91\",\"ownerId\":\"%s\"}".formatted(ownerId);
        UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                jdbcTemplate, workspaceId, channelId, "delete_comment", target, "{}", key, "antispam");

        assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                .isEqualTo("succeeded"));

        UUID again = remoteActionService.enqueue(
                "delete_comment",
                workspaceId,
                channelId,
                target,
                "{}",
                key,
                Instant.now().minusSeconds(1),
                "antispam",
                "{}",
                null);
        assertThat(again).isEqualTo(actionId);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM social_remote_actions WHERE idempotency_key = ?", Integer.class, key);
        assertThat(rows).isEqualTo(1);

        String comments = emulator().get("/_control/comments");
        assertThat(comments).contains("\"deleted\":true");
    }

    @Test
    @DisplayName("permission permanent: VK error 27 on delete_comment → failed_permanent/permission")
    void deleteCommentPermissionPermanent() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        String ownerId = "-1003302";
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", ownerId, "group");

        // Note: forcedError 15 on delete* is treated as alreadyGone success by the VK adapter;
        // use 27 (group auth / permission) for a true permanent failure on delete_comment.
        emulator().putScenario("{\"networks\":{\"vk\":{\"forcedError\":\"27\"}}}");
        try {
            String key = "be33-vk-perm-" + UuidV7.generate();
            String target =
                    "{\"commentType\":\"wall\",\"remoteCommentId\":\"1\",\"ownerId\":\"%s\"}".formatted(ownerId);
            UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                    jdbcTemplate, workspaceId, channelId, "delete_comment", target, "{}", key, "manual");

            assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                        .isEqualTo("failed_permanent");
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT error_code FROM social_remote_actions WHERE id = ?", String.class, actionId))
                        .isEqualTo("permission");
            });
        } finally {
            emulator().putScenario("{\"networks\":{\"vk\":{}}}");
        }
    }

    @Test
    @DisplayName("permission permanent: VK error 15 on publish_suggested → failed_permanent/permission")
    void publishSuggestedPermissionFifteen() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        String ownerId = "-1003307";
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", ownerId, "group");

        emulator()
                .put(
                        "/_control/vk/suggests",
                        """
                        {"ownerId":"%s","items":[{"id":99,"text":"blocked suggest"}]}
                        """
                                .formatted(ownerId));
        emulator().putScenario("{\"networks\":{\"vk\":{\"forcedError\":\"15\"}}}");
        try {
            String key = "be33-vk-sug-perm-" + UuidV7.generate();
            String target = "{\"remotePostId\":\"99\",\"ownerId\":\"%s\"}".formatted(ownerId);
            UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                    jdbcTemplate, workspaceId, channelId, "publish_suggested", target, "{}", key, "suggest");

            assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                        .isEqualTo("failed_permanent");
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT error_code FROM social_remote_actions WHERE id = ?", String.class, actionId))
                        .isEqualTo("permission");
            });
        } finally {
            emulator().putScenario("{\"networks\":{\"vk\":{}}}");
        }
    }

    @Test
    @DisplayName("publish_suggested → succeeded + remote_post_registry origin=suggest isOwn")
    void publishSuggestedRegistersOwn() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        String ownerId = "-1003303";
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", ownerId, "group");

        emulator()
                .put(
                        "/_control/vk/suggests",
                        """
                        {"ownerId":"%s","items":[{"id":42,"text":"please approve"}]}
                        """
                                .formatted(ownerId));

        String key = "be33-vk-suggest-" + UuidV7.generate();
        String target = "{\"remotePostId\":\"42\",\"ownerId\":\"%s\"}".formatted(ownerId);
        String payload = "{\"signed\":true}";
        UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                jdbcTemplate, workspaceId, channelId, "publish_suggested", target, payload, key, "suggest");

        assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                .isEqualTo("succeeded"));

        String remoteId = jdbcTemplate.queryForObject(
                "SELECT result->>'remoteId' FROM social_remote_actions WHERE id = ?", String.class, actionId);
        assertThat(remoteId).isNotBlank();
        assertThat(remotePostRegistryJdbc.isOwn(channelId, remoteId)).isTrue();

        String origin = jdbcTemplate.queryForObject(
                """
                SELECT origin FROM remote_post_registry
                WHERE channel_id = ? AND remote_post_id = ?
                """,
                String.class,
                channelId,
                remoteId);
        assertThat(origin).isEqualTo("suggest");
    }

    @Test
    @DisplayName("concurrency: two process() races on claimQueued → attempts==1, one succeeded")
    void concurrencySingleClaim() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        String ownerId = "-1003304";
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", ownerId, "group");

        emulator()
                .put(
                        "/_control/comments",
                        """
                        {"items":[{"network":"vk","ownerId":"%s","commentId":"77","type":"wall"}]}
                        """
                                .formatted(ownerId));

        String key = "be33-vk-race-" + UuidV7.generate();
        String target = "{\"commentType\":\"wall\",\"remoteCommentId\":\"77\",\"ownerId\":\"%s\"}".formatted(ownerId);
        UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                jdbcTemplate, workspaceId, channelId, "delete_comment", target, "{}", key, "manual");

        jdbcTemplate.update(
                """
                UPDATE social_remote_actions
                SET state = 'queued', locked_until = now() + interval '10 minutes'
                WHERE id = ?
                """,
                actionId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        int trueAcks;
        try {
            Future<Boolean> f1 = pool.submit(() -> remoteActionProcessor.process(actionId));
            Future<Boolean> f2 = pool.submit(() -> remoteActionProcessor.process(actionId));
            trueAcks = 0;
            if (Boolean.TRUE.equals(f1.get(30, TimeUnit.SECONDS))) {
                trueAcks++;
            }
            if (Boolean.TRUE.equals(f2.get(30, TimeUnit.SECONDS))) {
                trueAcks++;
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(trueAcks).isEqualTo(2);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                    .isEqualTo("succeeded");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT attempts FROM social_remote_actions WHERE id = ?", Integer.class, actionId))
                    .isEqualTo(1);
        });
    }

    @Test
    @DisplayName("registry from publication: VK publish upserts remote_post_registry isOwn")
    void registryFromPublication() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", "-1003305", "group");
        UUID publicationId = PipelineTestSupport.insertVkPublication(
                jdbcTemplate,
                workspaceId,
                UuidV7.generate(),
                UuidV7.generate(),
                channelId,
                Instant.now().minusSeconds(2),
                "be33-vk-pub-registry");

        assertThat(dispatchService.dispatchDueBatch()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM publications WHERE id = ?", String.class, publicationId))
                .isEqualTo("succeeded"));

        String remotePostId = jdbcTemplate.queryForObject(
                "SELECT remote_post_id FROM publications WHERE id = ?", String.class, publicationId);
        assertThat(remotePostId).isNotBlank();
        assertThat(remotePostRegistryJdbc.isOwn(channelId, remotePostId)).isTrue();

        String origin = jdbcTemplate.queryForObject(
                """
                SELECT origin FROM remote_post_registry
                WHERE channel_id = ? AND remote_post_id = ?
                """,
                String.class,
                channelId,
                remotePostId);
        assertThat(origin).isEqualTo("publication");
    }

    @Test
    @DisplayName("publish intent race: insertIntent → isIntentOwn before publication completes")
    void publishIntentRace() {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", "-1003306", "group");
        String fingerprint = "fp-be33-" + UuidV7.generate();
        String idem = "intent-" + UuidV7.generate();

        remotePostRegistryJdbc.insertIntent(
                channelId, idem, fingerprint, Instant.now().plus(Duration.ofHours(1)));

        assertThat(remotePostRegistryJdbc.isIntentOwn(channelId, fingerprint)).isTrue();
        assertThat(remotePostRegistryJdbc.isIntentOwnByKey(channelId, idem)).isTrue();
        assertThat(remotePostRegistryJdbc.isOwnByFingerprint(
                        channelId, fingerprint, Instant.now().minusSeconds(5)))
                .isFalse();

        remotePostRegistryJdbc.upsert(
                workspaceId,
                "vk",
                channelId,
                "remote-intent-1",
                "publication",
                null,
                null,
                null,
                (short) 0,
                fingerprint);
        assertThat(remotePostRegistryJdbc.isOwnByFingerprint(
                        channelId, fingerprint, Instant.now().minusSeconds(5)))
                .isTrue();
        assertThat(remotePostRegistryJdbc.isOwn(channelId, "remote-intent-1")).isTrue();
    }

    @Test
    @DisplayName("VK execute batching: 12 queued delete_comment → all succeeded, pending depth drops")
    void executeBatchingTwelveDeleteComments() throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        String ownerId = "-1003310";
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", ownerId, "group");

        StringBuilder commentsJson = new StringBuilder("{\"items\":[");
        List<UUID> actionIds = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            if (i > 0) {
                commentsJson.append(',');
            }
            String commentId = String.valueOf(200 + i);
            commentsJson
                    .append("{\"network\":\"vk\",\"ownerId\":\"")
                    .append(ownerId)
                    .append("\",\"commentId\":\"")
                    .append(commentId)
                    .append("\",\"type\":\"wall\"}");
            String target = "{\"commentType\":\"wall\",\"remoteCommentId\":\"%s\",\"ownerId\":\"%s\"}"
                    .formatted(commentId, ownerId);
            actionIds.add(PipelineTestSupport.enqueueRemoteAction(
                    jdbcTemplate,
                    workspaceId,
                    channelId,
                    "delete_comment",
                    target,
                    "{}",
                    "be33-vk-batch-" + i + "-" + UuidV7.generate(),
                    "antispam"));
        }
        commentsJson.append("]}");
        emulator().put("/_control/comments", commentsJson.toString());

        // Keep siblings queued so process() takes the execute-batch path (depth > 10).
        String idList = actionIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
        jdbcTemplate.update(
                """
                UPDATE social_remote_actions
                SET state = 'queued', locked_until = now() + interval '10 minutes'
                WHERE id IN (%s)
                """
                        .formatted(idList));

        assertThat(remoteActionProcessor.process(actionIds.getFirst())).isTrue();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Integer pending = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM social_remote_actions
                    WHERE channel_id = ? AND state IN ('pending','queued','in_progress','failed_retryable','unknown_outcome')
                    """,
                    Integer.class,
                    channelId);
            assertThat(pending).isZero();
            Integer succeeded = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM social_remote_actions
                    WHERE channel_id = ? AND state = 'succeeded'
                    """,
                    Integer.class,
                    channelId);
            assertThat(succeeded).isEqualTo(12);
        });

        String comments = emulator().get("/_control/comments");
        assertThat(comments).contains("\"deleted\":true");
    }

    static Stream<Arguments> vkModerationKindCases() {
        String owner = "-1003311";
        return Stream.of(
                Arguments.of(
                        "ban_user",
                        "{\"remoteUserId\":\"9001\",\"ownerId\":\"%s\"}".formatted(owner),
                        "{\"banReason\":1}"),
                Arguments.of("unban_user", "{\"remoteUserId\":\"9002\",\"ownerId\":\"%s\"}".formatted(owner), "{}"),
                Arguments.of(
                        "restore_comment",
                        "{\"commentType\":\"wall\",\"remoteCommentId\":\"55\",\"ownerId\":\"%s\"}".formatted(owner),
                        "{}"),
                Arguments.of(
                        "reply_comment",
                        "{\"remotePostId\":\"1\",\"remoteCommentId\":\"2\",\"ownerId\":\"%s\"}".formatted(owner),
                        "{\"text\":\"ack\"}"),
                Arguments.of(
                        "notify_author",
                        "{\"remoteUserId\":\"9003\",\"ownerId\":\"%s\"}".formatted(owner),
                        "{\"text\":\"hi\"}"),
                Arguments.of("delete_post", "{\"remotePostId\":\"88\",\"ownerId\":\"%s\"}".formatted(owner), "{}"));
    }

    @ParameterizedTest(name = "VK kind {0}")
    @MethodSource("vkModerationKindCases")
    @DisplayName("VK moderation kinds succeed against emulator")
    void vkModerationKinds(String kind, String target, String payload) throws Exception {
        UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
        String ownerId = "-1003311";
        UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", ownerId, "group");

        if ("restore_comment".equals(kind)) {
            emulator()
                    .put(
                            "/_control/comments",
                            """
                            {"items":[{"network":"vk","ownerId":"%s","commentId":"55","type":"wall","deleted":true}]}
                            """
                                    .formatted(ownerId));
        }
        if ("delete_post".equals(kind)) {
            emulator()
                    .put(
                            "/_control/posts",
                            """
                            {"items":[{"network":"vk","channelId":"%s","remotePostId":"88","text":"to-delete"}]}
                            """
                                    .formatted(ownerId));
        }
        if ("unban_user".equals(kind)) {
            String banKey = "be33-vk-preban-" + UuidV7.generate();
            UUID banId = PipelineTestSupport.enqueueRemoteAction(
                    jdbcTemplate,
                    workspaceId,
                    channelId,
                    "ban_user",
                    "{\"remoteUserId\":\"9002\",\"ownerId\":\"-1003311\"}",
                    "{}",
                    banKey,
                    "manual");
            assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM social_remote_actions WHERE id = ?", String.class, banId))
                    .isEqualTo("succeeded"));
        }

        String key = "be33-vk-kind-" + kind + "-" + UuidV7.generate();
        UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                jdbcTemplate, workspaceId, channelId, kind, target, payload, key, "manual");

        assertThat(dispatchService.dispatchDueModerationActions()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                .isEqualTo("succeeded"));
    }
}
