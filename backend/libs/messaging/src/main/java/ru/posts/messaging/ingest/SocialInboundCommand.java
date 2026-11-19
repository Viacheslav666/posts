package ru.posts.messaging.ingest;

import java.time.Instant;

/**
 * Raw social webhook payload enqueued by posts-api for posts-ingest (BE-32 / ADR-0005).
 *
 * @param network routing key: {@code vk} | {@code tg} | {@code max}
 * @param resourceId channelId (VK/Max) or botId (Telegram)
 * @param payloadJson raw JSON body (secrets stripped by publisher)
 * @param enqueuedAt when the hook accepted the payload
 * @param traceId W3C / internal trace id
 * @param retryCounter VK {@code X-Retry-Counter} (nullable)
 */
public record SocialInboundCommand(
        String network,
        String resourceId,
        String payloadJson,
        Instant enqueuedAt,
        String traceId,
        Integer retryCounter) {}
