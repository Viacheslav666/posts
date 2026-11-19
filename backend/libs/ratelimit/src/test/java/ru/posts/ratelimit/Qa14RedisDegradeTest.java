package ru.posts.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.posts.ratelimit.Bucket4jSocialRateLimiter.LimitSpec;
import ru.posts.ratelimit.Bucket4jSocialRateLimiter.NetworkLimitConfig;

/** QA-14 chaos: Redis unavailable → local limiter degrades without throwing (no event loss). */
@Tag("ci")
class Qa14RedisDegradeTest {

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("TC-IN-redis-degrade: Redis error → local fallback allow")
    void redisDownUsesLocalFallback() {
        ProxyManager<byte[]> proxyManager = Mockito.mock(ProxyManager.class);
        when(proxyManager.builder()).thenThrow(new RuntimeException("redis down"));

        Bucket4jSocialRateLimiter limiter = new Bucket4jSocialRateLimiter(
                proxyManager,
                Map.of(
                        "vk",
                        new NetworkLimitConfig(
                                LimitSpec.perSecond(10), LimitSpec.perSecond(10), LimitSpec.perMinute(100))),
                new SimpleMeterRegistry());

        RateLimitDecision decision = limiter.tryAcquire("vk", "token-x", "-100");
        assertThat(decision.allowed()).isTrue();
    }
}
