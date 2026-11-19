package ru.posts.social.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ru.posts.social.inbound.metrics.SocialInboundMetrics;

class SocialEventDispatcherTest {

    @Test
    void consumedStopsChain() {
        AtomicInteger antispam = new AtomicInteger();
        AtomicInteger suggest = new AtomicInteger();
        SocialEventHandler h1 = new SocialEventHandler() {
            @Override
            public String consumerKey() {
                return "antispam";
            }

            @Override
            public boolean supports(SocialEventKind kind, String network) {
                return true;
            }

            @Override
            public int order() {
                return 10;
            }

            @Override
            public HandlerResult handle(SocialInboundEvent event, HandlerContext ctx) {
                antispam.incrementAndGet();
                return HandlerResult.CONSUMED;
            }
        };
        SocialEventHandler h2 = new SocialEventHandler() {
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
                return 20;
            }

            @Override
            public HandlerResult handle(SocialInboundEvent event, HandlerContext ctx) {
                suggest.incrementAndGet();
                return HandlerResult.CONTINUE;
            }
        };
        SocialEventDispatcher dispatcher =
                new SocialEventDispatcher(List.of(h2, h1), new SocialInboundMetrics(new SimpleMeterRegistry()));
        SocialInboundEvent event = new SocialInboundEvent(
                "vk",
                UUID.randomUUID(),
                UUID.randomUUID(),
                SocialEventKind.POST_NEW,
                "d1",
                Map.of(),
                new SocialInboundAuthor("1", false, false),
                "t",
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                "{}",
                List.of("antispam", "suggest"));
        dispatcher.dispatch(event);
        assertThat(antispam.get()).isEqualTo(1);
        assertThat(suggest.get()).isEqualTo(0);
    }
}
