package ru.posts.publisher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;
import ru.posts.publisher.service.RemoteActionProcessor;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.ModerationFeatureFlags;
import ru.posts.social.api.ModerationResult;
import ru.posts.social.api.Network;
import ru.posts.social.api.NetworkCapabilities;
import ru.posts.social.api.SocialApiException;
import ru.posts.social.api.SocialModerationPort;

/**
 * BE-33: UNKNOWN_OUTCOME on publish_suggested → reconcilePublishSuggested recovers without a second
 * publish (0 duplicates).
 *
 * <p>Uses an in-process {@link SocialModerationPort} stub (same Spring context as other VK ITs) so a
 * second {@code @MockitoBean} context cannot steal {@code actions.moderation.vk} deliveries.
 */
@Tag("ci")
class Be33PublishSuggestedReconcileTest extends AbstractVkPipelineIT {

    @Autowired
    private RemoteActionProcessor remoteActionProcessor;

    @Autowired
    private RemotePostRegistryJdbc remotePostRegistryJdbc;

    @Test
    @DisplayName("UNKNOWN_OUTCOME → reconcile → succeeded once + registry once (no re-publish)")
    @SuppressWarnings("unchecked")
    void unknownOutcomeReconcilesWithoutRepublish() throws Exception {
        SocialModerationPort port = mock(SocialModerationPort.class);
        when(port.network()).thenReturn(Network.VK);
        when(port.moderationCapabilities())
                .thenReturn(new NetworkCapabilities(Network.VK, true, true, true, allFeaturesEnabled()));
        when(port.classifyError(any())).thenAnswer(inv -> {
            Throwable t = inv.getArgument(0);
            if (t instanceof SocialApiException social) {
                return social.errorClass();
            }
            return ErrorClass.RETRYABLE;
        });
        String remoteId = "4242";
        when(port.publishSuggested(any(), any(), any()))
                .thenThrow(new SocialApiException(ErrorClass.UNKNOWN_OUTCOME, "simulated hang after wall.post"));
        when(port.reconcilePublishSuggested(any(), any(), anyString()))
                .thenReturn(Optional.of(new ModerationResult(remoteId, Map.of("reconciled", true))));

        ObjectProvider<SocialModerationPort> original = (ObjectProvider<SocialModerationPort>)
                ReflectionTestUtils.getField(remoteActionProcessor, "moderationPortProvider");
        ObjectProvider<SocialModerationPort> stubProvider = mock(ObjectProvider.class);
        when(stubProvider.getIfAvailable()).thenReturn(port);
        when(stubProvider.getObject()).thenReturn(port);
        ReflectionTestUtils.setField(remoteActionProcessor, "moderationPortProvider", stubProvider);
        try {
            UUID workspaceId = PipelineTestSupport.seedWorkspace(jdbcTemplate);
            String ownerId = "-1003401";
            UUID channelId = PipelineTestSupport.seedChannel(jdbcTemplate, workspaceId, "vk", ownerId, "group");

            String key = "be33-vk-reconcile-" + UuidV7.generate();
            String target = "{\"remotePostId\":\"99\",\"ownerId\":\"%s\"}".formatted(ownerId);
            UUID actionId = PipelineTestSupport.enqueueRemoteAction(
                    jdbcTemplate, workspaceId, channelId, "publish_suggested", target, "{}", key, "suggest");

            jdbcTemplate.update(
                    """
                    UPDATE social_remote_actions
                    SET state = 'queued', locked_until = now() + interval '10 minutes'
                    WHERE id = ?
                    """,
                    actionId);

            assertThat(remoteActionProcessor.process(actionId)).isTrue();

            assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM social_remote_actions WHERE id = ?", String.class, actionId))
                    .isEqualTo("succeeded");
            assertThat(remotePostRegistryJdbc.isOwn(channelId, remoteId)).isTrue();
            Integer registryRows = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM remote_post_registry
                    WHERE channel_id = ? AND remote_post_id = ?
                    """,
                    Integer.class,
                    channelId,
                    remoteId);
            assertThat(registryRows).isEqualTo(1);

            verify(port, times(1)).publishSuggested(any(), any(), any());
            verify(port, times(1)).reconcilePublishSuggested(any(), any(), anyString());
            verify(port, never()).executeBatch(any(), any());
        } finally {
            ReflectionTestUtils.setField(remoteActionProcessor, "moderationPortProvider", original);
        }
    }

    private static Map<String, Boolean> allFeaturesEnabled() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put(ModerationFeatureFlags.PUBLISH_SUGGESTED, true);
        features.put(ModerationFeatureFlags.DELETE_POST, true);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_WALL, true);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_PHOTO, true);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_VIDEO, true);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_BOARD, true);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_MARKET, true);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_DISCUSSION, true);
        features.put(ModerationFeatureFlags.DELETE_COMMENT_CHANNEL_COMMENT, true);
        features.put(ModerationFeatureFlags.RESTORE_COMMENT, true);
        features.put(ModerationFeatureFlags.BAN_USER, true);
        features.put(ModerationFeatureFlags.REPLY_TO_COMMENT, true);
        features.put(ModerationFeatureFlags.NOTIFY_AUTHOR, true);
        return features;
    }
}
