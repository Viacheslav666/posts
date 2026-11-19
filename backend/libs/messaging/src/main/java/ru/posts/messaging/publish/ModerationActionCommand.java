package ru.posts.messaging.publish;

import java.util.UUID;

/** Minimal AMQP payload for social_remote_actions — state lives in PostgreSQL (BE-33). */
public record ModerationActionCommand(UUID actionId, String traceparent) {}
