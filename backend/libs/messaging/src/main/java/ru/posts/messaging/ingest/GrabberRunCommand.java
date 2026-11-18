package ru.posts.messaging.ingest;

import java.util.UUID;

/** RabbitMQ payload for ingest.grabber (BE-17). */
public record GrabberRunCommand(UUID ruleId, UUID runId, String trigger, String traceId) {}
