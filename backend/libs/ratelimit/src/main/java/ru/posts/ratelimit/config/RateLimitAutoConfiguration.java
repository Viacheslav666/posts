package ru.posts.ratelimit.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.posts.ratelimit.AllowAllSocialRateLimiter;
import ru.posts.ratelimit.Bucket4jSocialRateLimiter;
import ru.posts.ratelimit.Bucket4jSocialRateLimiter.LimitSpec;
import ru.posts.ratelimit.Bucket4jSocialRateLimiter.NetworkLimitConfig;
import ru.posts.ratelimit.SocialRateLimiter;

@AutoConfiguration
@ConditionalOnClass(LettuceBasedProxyManager.class)
@EnableConfigurationProperties(PostsRateLimitProperties.class)
public class RateLimitAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "posts.ratelimit.enabled", havingValue = "true", matchIfMissing = true)
    RedisClient postsRateLimitRedisClient(PostsRateLimitProperties properties) {
        RedisURI uri = RedisURI.create(properties.redisUri());
        return RedisClient.create(uri);
    }

    @Bean
    @ConditionalOnMissingBean(name = "postsRateLimitProxyManager")
    @ConditionalOnProperty(name = "posts.ratelimit.enabled", havingValue = "true", matchIfMissing = true)
    ProxyManager<byte[]> postsRateLimitProxyManager(RedisClient postsRateLimitRedisClient) {
        return LettuceBasedProxyManager.builderFor(postsRateLimitRedisClient)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(SocialRateLimiter.class)
    @ConditionalOnProperty(name = "posts.ratelimit.enabled", havingValue = "true", matchIfMissing = true)
    SocialRateLimiter socialRateLimiter(
            ProxyManager<byte[]> postsRateLimitProxyManager,
            PostsRateLimitProperties properties,
            MeterRegistry meterRegistry) {
        Map<String, NetworkLimitConfig> limits = new HashMap<>();
        properties
                .networks()
                .forEach((network, cfg) -> limits.put(
                        network,
                        new NetworkLimitConfig(
                                LimitSpec.perSecond(cfg.globalPerSecond()),
                                LimitSpec.perSecond(cfg.tokenPerSecond()),
                                LimitSpec.perMinute(cfg.chatPerMinute()))));
        for (String network : new String[] {"vk", "tg", "ok", "max", "rutube"}) {
            limits.putIfAbsent(network, NetworkLimitConfig.defaultsFor(network));
        }
        return new Bucket4jSocialRateLimiter(postsRateLimitProxyManager, limits, meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(SocialRateLimiter.class)
    @ConditionalOnProperty(name = "posts.ratelimit.enabled", havingValue = "false")
    SocialRateLimiter allowAllSocialRateLimiter() {
        return new AllowAllSocialRateLimiter();
    }
}
