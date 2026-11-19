package ru.posts.antispam.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import ru.posts.antispam.engine.TextNormalizer;

/** Tracks identical author+text within a sliding window (default 12h). */
public class RepeatDetector {

    private final ObjectProvider<StringRedisTemplate> redis;
    private final Cache<String, AtomicInteger> local =
            Caffeine.newBuilder().maximumSize(200_000).expireAfterWrite(Duration.ofHours(12)).build();

    public RepeatDetector(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis;
    }

    /**
     * Records occurrence and returns {@code true} when this is a repeat (count after increment &gt;
     * 1).
     */
    public boolean isRepeat(UUID channelId, String authorId, String text, Duration window) {
        String key = "antispam:repeat:" + channelId + ":" + authorId + ":" + hash(TextNormalizer.normalizeForKeywords(text));
        Duration ttl = window == null ? Duration.ofHours(12) : window;
        StringRedisTemplate template = redis.getIfAvailable();
        if (template != null) {
            try {
                Long count = template.opsForValue().increment(key);
                if (count != null && count == 1L) {
                    template.expire(key, ttl);
                }
                return count != null && count > 1L;
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        AtomicInteger counter = local.get(key, k -> new AtomicInteger(0));
        int n = counter.incrementAndGet();
        return n > 1;
    }

    private static String hash(String normalized) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(normalized.hashCode());
        }
    }
}
