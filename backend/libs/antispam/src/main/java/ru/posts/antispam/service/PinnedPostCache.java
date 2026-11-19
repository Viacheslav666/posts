package ru.posts.antispam.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;

/** In-memory cache of pinned post ids per channel. */
public class PinnedPostCache {

    private final Cache<UUID, String> pinned = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public void put(UUID channelId, String remotePostId) {
        if (channelId != null && remotePostId != null) {
            pinned.put(channelId, remotePostId);
        }
    }

    public boolean isUnderPinned(UUID channelId, String remotePostId) {
        if (channelId == null || remotePostId == null) {
            return false;
        }
        String pinnedId = pinned.getIfPresent(channelId);
        return pinnedId != null && pinnedId.equals(remotePostId);
    }
}
