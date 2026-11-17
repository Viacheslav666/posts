package ru.posts.messaging.publish;

import java.util.UUID;

/** Minimal RabbitMQ payload for publish.<network> (ADR-0005). */
public record PublishCommand(UUID publicationId, String traceparent) {}
