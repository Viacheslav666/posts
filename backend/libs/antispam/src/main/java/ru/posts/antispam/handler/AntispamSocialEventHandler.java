package ru.posts.antispam.handler;

import ru.posts.antispam.service.AntispamEventProcessor;
import ru.posts.social.inbound.HandlerContext;
import ru.posts.social.inbound.HandlerResult;
import ru.posts.social.inbound.SocialEventHandler;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundEvent;

/** SPI handler (order 10). Consumes POST_SUGGESTED on match so suggest does not publish. */
public class AntispamSocialEventHandler implements SocialEventHandler {

    private final AntispamEventProcessor processor;

    public AntispamSocialEventHandler(AntispamEventProcessor processor) {
        this.processor = processor;
    }

    @Override
    public String consumerKey() {
        return "antispam";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean supports(SocialEventKind kind, String network) {
        if (kind == null) {
            return false;
        }
        return switch (kind) {
            case COMMENT_NEW, COMMENT_EDITED, MESSAGE_NEW, POST_NEW, POST_SUGGESTED -> true;
            default -> false;
        };
    }

    @Override
    public HandlerResult handle(SocialInboundEvent event, HandlerContext ctx) {
        return processor.process(event);
    }
}
