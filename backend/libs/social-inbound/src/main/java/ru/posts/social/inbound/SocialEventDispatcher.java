package ru.posts.social.inbound;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.posts.social.inbound.metrics.SocialInboundMetrics;

/** Dispatches normalized events to subscribed handlers in order. */
public class SocialEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SocialEventDispatcher.class);

    private final List<SocialEventHandler> handlers;
    private final SocialInboundMetrics metrics;

    public SocialEventDispatcher(List<SocialEventHandler> handlers, SocialInboundMetrics metrics) {
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(SocialEventHandler::order))
                .toList();
        this.metrics = metrics;
    }

    public void dispatch(SocialInboundEvent event) {
        Set<String> allowed = new HashSet<>(event.consumers() == null ? List.of() : event.consumers());
        HandlerContext ctx = new HandlerContext(new HashMap<>());
        for (SocialEventHandler handler : handlers) {
            if (!allowed.isEmpty() && !allowed.contains(handler.consumerKey())) {
                continue;
            }
            if (!handler.supports(event.kind(), event.network())) {
                continue;
            }
            HandlerResult result = handler.handle(event, ctx);
            log.debug(
                    "social_inbound_handler consumer={} kind={} network={} result={}",
                    handler.consumerKey(),
                    event.kind(),
                    event.network(),
                    result);
            if (result == HandlerResult.CONSUMED) {
                metrics.recordResult(event.network(), event.kind().name(), "consumed");
                return;
            }
        }
        metrics.recordResult(event.network(), event.kind().name(), "handled");
    }
}
