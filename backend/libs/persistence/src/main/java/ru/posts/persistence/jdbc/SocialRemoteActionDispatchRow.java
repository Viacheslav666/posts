package ru.posts.persistence.jdbc;

import java.time.Instant;
import java.util.UUID;

/** Row claimed by scheduler for actions.moderation.&lt;network&gt; enqueue (BE-33). */
public record SocialRemoteActionDispatchRow(UUID id, Instant runAt, String network, UUID workspaceId, UUID channelId) {}
