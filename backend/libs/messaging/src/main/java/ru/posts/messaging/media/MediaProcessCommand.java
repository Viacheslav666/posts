package ru.posts.messaging.media;

import java.util.UUID;

/** RabbitMQ payload for media.process (ADR-0005). */
public record MediaProcessCommand(UUID mediaId, String traceparent) {}
