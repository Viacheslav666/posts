package ru.posts.social.inbound;

/**
 * SPI for inbound social event consumers (antispam / suggest / crosspost).
 * Handlers must be idempotent (at-least-once delivery).
 */
public interface SocialEventHandler {

    /** Consumer key matching {@code channel_event_subscriptions.consumers}. */
    String consumerKey();

    boolean supports(SocialEventKind kind, String network);

    /** Lower runs first. Antispam=10, suggest=20, crosspost=30. */
    default int order() {
        return 100;
    }

    HandlerResult handle(SocialInboundEvent event, HandlerContext ctx);
}
