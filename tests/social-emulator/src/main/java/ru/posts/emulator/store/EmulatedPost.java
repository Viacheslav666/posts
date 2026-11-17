package ru.posts.emulator.store;

import java.time.Instant;
import java.util.Map;

public record EmulatedPost(
        String id,
        Network network,
        String channelId,
        String remotePostId,
        String idempotencyKey,
        String text,
        Instant createdAt,
        Map<String, Object> metadata) {}
