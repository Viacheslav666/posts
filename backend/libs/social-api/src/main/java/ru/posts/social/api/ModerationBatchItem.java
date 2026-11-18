package ru.posts.social.api;

import java.util.UUID;

/** One item in a VK {@code execute} (or sequential) moderation batch. */
public record ModerationBatchItem(
        String kind, UUID actionId, ModerationTarget target, ModerationPayload payload, String idempotencyKey) {}
