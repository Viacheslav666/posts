package ru.posts.persistence.jdbc;

import java.time.Instant;
import java.util.UUID;

/** Minimal projection returned by the scheduler SKIP LOCKED claim. */
public record PublicationDispatchRow(UUID id, Instant runAt, String network, UUID workspaceId) {}
