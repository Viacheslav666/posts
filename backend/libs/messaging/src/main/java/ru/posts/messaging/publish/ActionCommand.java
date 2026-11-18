package ru.posts.messaging.publish;

import java.util.UUID;

/** Minimal AMQP payload for scheduled_actions — state lives in PostgreSQL (ADR-0004/0005). */
public record ActionCommand(UUID actionId, String traceparent) {}
