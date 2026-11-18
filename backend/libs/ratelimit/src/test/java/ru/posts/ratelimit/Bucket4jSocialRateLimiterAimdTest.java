package ru.posts.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.posts.ratelimit.Bucket4jSocialRateLimiter.LimitSpec;
import ru.posts.ratelimit.Bucket4jSocialRateLimiter.NetworkLimitConfig;

class Bucket4jSocialRateLimiterAimdTest {

    @Test
    @SuppressWarnings("unchecked")
    void penalizeBlocksSubsequentAcquire() {
        ProxyManager<byte[]> proxyManager = Mockito.mock(ProxyManager.class);
        Bucket4jSocialRateLimiter limiter = new Bucket4jSocialRateLimiter(
                proxyManager,
                Map.of(
                        "tg",
                        new NetworkLimitConfig(
                                LimitSpec.perSecond(100), LimitSpec.perSecond(100), LimitSpec.perMinute(100))),
                new SimpleMeterRegistry());

        limiter.penalize("tg", "acct-1", "chat-1", Duration.ofSeconds(30));
        RateLimitDecision decision = limiter.tryAcquire("tg", "acct-1", "chat-1");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.waitTime()).isGreaterThan(Duration.ofSeconds(1));
    }
}
