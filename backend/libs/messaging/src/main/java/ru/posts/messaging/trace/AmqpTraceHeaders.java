package ru.posts.messaging.trace;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.amqp.core.MessagePostProcessor;

/**
 * W3C {@code traceparent} propagation for AMQP (04-devops §6). Generates a valid traceparent when
 * the caller does not supply one so publisher/scheduler spans stay correlatable.
 */
public final class AmqpTraceHeaders {

    public static final String TRACEPARENT = "traceparent";

    private AmqpTraceHeaders() {}

    public static String currentOrNew(String existing) {
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        // W3C: version-trace_id(32 hex)-parent_id(16 hex)-flags
        String tid = UUID.randomUUID().toString().replace("-", "");
        String pid = String.format("%016x", ThreadLocalRandom.current().nextLong());
        return "00-" + tid + "-" + pid + "-01";
    }

    public static MessagePostProcessor withTraceparent(String traceparent) {
        String value = currentOrNew(traceparent);
        return message -> {
            message.getMessageProperties().setHeader(TRACEPARENT, value);
            return message;
        };
    }
}
