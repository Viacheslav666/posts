package ru.posts.persistence.jdbc;

import java.time.Instant;
import java.util.UUID;

/** Row claimed by scheduler for actions.<network> enqueue. */
public record ScheduledActionDispatchRow(UUID id, Instant runAt, String network, UUID workspaceId) {}
