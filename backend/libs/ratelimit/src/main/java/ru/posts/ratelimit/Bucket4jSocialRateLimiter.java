package ru.posts.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multi-level token buckets: {@code rl:{network}:global}, {@code rl:{network}:token:{id}}, {@code
 * rl:{network}:chat:{externalId}}. Falls back to a local Resilience4j limiter when Redis is down.
 * AIMD: {@link #penalize} temporarily blocks keys after 429/flood.
 */
public class Bucket4jSocialRateLimiter implements SocialRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(Bucket4jSocialRateLimiter.class);

    private final ProxyManager<byte[]> proxyManager;
    private final Map<String, NetworkLimitConfig> limits;
    private final MeterRegistry meterRegistry;
    private final Map<String, RateLimiter> localFallback = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> penalties = new ConcurrentHashMap<>();
    private volatile boolean redisDegraded;

    public Bucket4jSocialRateLimiter(
            ProxyManager<byte[]> proxyManager, Map<String, NetworkLimitConfig> limits, MeterRegistry meterRegistry) {
        this.proxyManager = proxyManager;
        this.limits = Map.copyOf(limits);
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RateLimitDecision tryAcquire(String network, String socialAccountId, String externalChatId) {
        NetworkLimitConfig config = limits.getOrDefault(network, NetworkLimitConfig.defaultsFor(network));
        Instant now = Instant.now();
        RateLimitDecision penalty = checkPenalty(network, socialAccountId, externalChatId, now);
        if (penalty != null) {
            return penalty;
        }
        try {
            RateLimitDecision global =
                    consume("rl:" + network + ":global", config.globalPerSecond(), "global", network);
            if (!global.allowed()) {
                return global;
            }
            if (socialAccountId != null && !socialAccountId.isBlank()) {
                RateLimitDecision token = consume(
                        "rl:" + network + ":token:" + socialAccountId, config.tokenPerSecond(), "token", network);
                if (!token.allowed()) {
                    return token;
                }
            }
            if (externalChatId != null && !externalChatId.isBlank()) {
                RateLimitDecision chat =
                        consume("rl:" + network + ":chat:" + externalChatId, config.chatPerMinute(), "chat", network);
                if (!chat.allowed()) {
                    return chat;
                }
            }
            redisDegraded = false;
            return RateLimitDecision.allow();
        } catch (RuntimeException ex) {
            if (!redisDegraded) {
                log.warn("Redis rate-limit unavailable, falling back to local limiter: {}", ex.toString());
                redisDegraded = true;
                meterRegistry
                        .counter("posts_ratelimit_redis_degraded_total", "network", network)
                        .increment();
            }
            return localFallback(network, config);
        }
    }

    @Override
    public void penalize(String network, String socialAccountId, String externalChatId, Duration blockFor) {
        Duration effective =
                blockFor == null || blockFor.isNegative() || blockFor.isZero() ? Duration.ofSeconds(5) : blockFor;
        Instant until = Instant.now().plus(effective);
        if (socialAccountId != null && !socialAccountId.isBlank()) {
            penalties.merge("rl:" + network + ":token:" + socialAccountId, until, Bucket4jSocialRateLimiter::later);
        }
        if (externalChatId != null && !externalChatId.isBlank()) {
            penalties.merge("rl:" + network + ":chat:" + externalChatId, until, Bucket4jSocialRateLimiter::later);
        }
        // VK flood control also slows the global key briefly
        if ("vk".equals(network) && effective.compareTo(Duration.ofMinutes(5)) >= 0) {
            penalties.merge("rl:" + network + ":global", until, Bucket4jSocialRateLimiter::later);
        }
        meterRegistry
                .counter("posts_ratelimit_throttled_total", "network", network, "level", "aimd_penalty")
                .increment();
    }

    private RateLimitDecision checkPenalty(String network, String socialAccountId, String externalChatId, Instant now) {
        String[] keys = {
            socialAccountId == null || socialAccountId.isBlank() ? null : "rl:" + network + ":token:" + socialAccountId,
            externalChatId == null || externalChatId.isBlank() ? null : "rl:" + network + ":chat:" + externalChatId,
            "rl:" + network + ":global"
        };
        Instant farthest = null;
        String hitLevel = null;
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            if (key == null) {
                continue;
            }
            Instant until = penalties.get(key);
            if (until == null) {
                continue;
            }
            if (until.isAfter(now)) {
                if (farthest == null || until.isAfter(farthest)) {
                    farthest = until;
                    hitLevel = i == 0 ? "token" : (i == 1 ? "chat" : "global");
                }
            } else {
                penalties.remove(key, until);
            }
        }
        if (farthest == null) {
            return null;
        }
        meterRegistry
                .counter("posts_ratelimit_throttled_total", "network", network, "level", hitLevel + "_penalty")
                .increment();
        return RateLimitDecision.deny(Duration.between(now, farthest));
    }

    private RateLimitDecision consume(String key, LimitSpec spec, String level, String network) {
        if (spec.capacity() <= 0) {
            return RateLimitDecision.allow();
        }
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(spec.capacity())
                        .refillGreedy(spec.refillTokens(), spec.refillPeriod())
                        .build())
                .build();
        BucketProxy bucket = proxyManager.builder().build(key.getBytes(StandardCharsets.UTF_8), () -> configuration);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitDecision.allow();
        }
        meterRegistry
                .counter("posts_ratelimit_throttled_total", "network", network, "level", level)
                .increment();
        long nanos =
                Math.max(probe.getNanosToWaitForRefill(), Duration.ofMillis(50).toNanos());
        return RateLimitDecision.deny(Duration.ofNanos(nanos));
    }

    private RateLimitDecision localFallback(String network, NetworkLimitConfig config) {
        RateLimiter limiter = localFallback.computeIfAbsent(network, n -> {
            int permits = Math.max(1, (int) config.globalPerSecond().capacity());
            RateLimiterConfig cfg = RateLimiterConfig.custom()
                    .limitForPeriod(permits)
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    .timeoutDuration(Duration.ZERO)
                    .build();
            return RateLimiter.of("local-" + n, cfg);
        });
        if (limiter.acquirePermission()) {
            return RateLimitDecision.allow();
        }
        meterRegistry
                .counter("posts_ratelimit_throttled_total", "network", network, "level", "local_fallback")
                .increment();
        return RateLimitDecision.deny(Duration.ofMillis(200));
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    /** Capacity + refill for one bucket level. */
    public record LimitSpec(long capacity, long refillTokens, Duration refillPeriod) {
        public static LimitSpec perSecond(long tokens) {
            return new LimitSpec(tokens, tokens, Duration.ofSeconds(1));
        }

        public static LimitSpec perMinute(long tokens) {
            return new LimitSpec(tokens, tokens, Duration.ofMinutes(1));
        }
    }

    /** Per-network limit configuration. */
    public record NetworkLimitConfig(LimitSpec globalPerSecond, LimitSpec tokenPerSecond, LimitSpec chatPerMinute) {
        public static NetworkLimitConfig defaultsFor(String network) {
            return switch (network) {
                case "tg" -> new NetworkLimitConfig(
                        LimitSpec.perSecond(30), LimitSpec.perSecond(30), LimitSpec.perMinute(20));
                case "vk" -> new NetworkLimitConfig(
                        LimitSpec.perSecond(20), LimitSpec.perSecond(3), LimitSpec.perMinute(60));
                case "max" -> new NetworkLimitConfig(
                        LimitSpec.perSecond(30), LimitSpec.perSecond(30), LimitSpec.perMinute(30));
                default -> new NetworkLimitConfig(
                        LimitSpec.perSecond(5), LimitSpec.perSecond(5), LimitSpec.perMinute(30));
            };
        }
    }
}
