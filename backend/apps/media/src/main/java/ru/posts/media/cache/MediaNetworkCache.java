package ru.posts.media.cache;

import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Cache of network upload ids keyed by sha256 + channel (for publisher). */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class MediaNetworkCache {

    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    public MediaNetworkCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public static String key(String sha256, String channelId) {
        return "media:upload:" + sha256 + ":" + channelId;
    }

    public void put(String sha256, String channelId, String networkUploadId) {
        redis.opsForValue().set(key(sha256, channelId), networkUploadId, TTL);
    }

    public Optional<String> get(String sha256, String channelId) {
        return Optional.ofNullable(redis.opsForValue().get(key(sha256, channelId)));
    }

    public void evict(String sha256, String channelId) {
        redis.delete(key(sha256, channelId));
    }
}
