package ru.posts.social.inbound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test/recording handler for BE-32 acceptance (IT / emulator). Registered when
 * {@code posts.social-inbound.recording-handler=true}.
 */
public class RecordingSocialEventHandler implements SocialEventHandler {

    private final CopyOnWriteArrayList<SocialInboundEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public String consumerKey() {
        return "suggest";
    }

    @Override
    public boolean supports(SocialEventKind kind, String network) {
        return true;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public HandlerResult handle(SocialInboundEvent event, HandlerContext ctx) {
        events.add(event);
        return HandlerResult.CONTINUE;
    }

    public List<SocialInboundEvent> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public void clear() {
        events.clear();
    }

    public int size() {
        return events.size();
    }
}
