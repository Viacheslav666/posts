package ru.posts.antispam.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Caches {@code groups.isMember} results for 1 hour. Prefers Redis when available, falls back to
 * local Caffeine (tests / single-node).
 */
public class MembershipCache {

    private static final String KEY_PREFIX = "antispam:member:";

    private final ObjectProvider<StringRedisTemplate> redis;
    private final Cache<String, Boolean> local = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    private final Duration ttl = Duration.ofHours(1);

    public MembershipCache(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis;
    }

    public boolean isMember(String groupId, String userId, BiFunction<String, String, Boolean> loader) {
        String key = KEY_PREFIX + groupId + ":" + userId;
        Boolean cached = get(key);
        if (cached != null) {
            return cached;
        }
        boolean member = Boolean.TRUE.equals(loader.apply(groupId, userId));
        put(key, member);
        return member;
    }

    private Boolean get(String key) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template != null) {
            try {
                String v = template.opsForValue().get(key);
                if (v != null) {
                    return "1".equals(v);
                }
            } catch (RuntimeException ignored) {
                // fall through to local
            }
        }
        return local.getIfPresent(key);
    }

    private void put(String key, boolean value) {
        local.put(key, value);
        StringRedisTemplate template = redis.getIfAvailable();
        if (template != null) {
            try {
                template.opsForValue().set(key, value ? "1" : "0", ttl);
            } catch (RuntimeException ignored) {
                // local cache still holds value
            }
        }
    }

    public Optional<Boolean> peek(String groupId, String userId) {
        return Optional.ofNullable(get(KEY_PREFIX + groupId + ":" + userId));
    }
}
