package ru.posts.social.inbound.album;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Aggregates Telegram {@code media_group_id} updates into one POST_NEW after {@link #WINDOW} of
 * silence. The backing store is shared (Postgres) so fragments that land on different replicas are
 * not flushed as a partial album.
 */
public class TelegramAlbumAggregator {

    public static final Duration WINDOW = Duration.ofSeconds(3);

    private final AlbumFragmentStore store;

    public TelegramAlbumAggregator(ObjectMapper objectMapper) {
        this(new InMemoryAlbumFragmentStore(objectMapper));
    }

    public TelegramAlbumAggregator(AlbumFragmentStore store) {
        this.store = store;
    }

    public void buffer(String mediaGroupId, String channelKey, String botId, JsonNode updateJson) {
        store.add(channelKey, mediaGroupId, botId, updateJson, Instant.now());
    }

    /** Returns and removes albums whose silence window has elapsed. */
    public List<ReadyAlbum> drainReady(Instant now) {
        return store.drainReady(now);
    }

    /** Test helper: force-drain is not supported for the shared store; returns empty. */
    public Optional<List<JsonNode>> forceDrain(String mediaGroupId, String channelKey) {
        return Optional.empty();
    }

    public int pendingCount() {
        return store.pendingCount();
    }

    public record ReadyAlbum(String channelKey, String mediaGroupId, String botId, List<JsonNode> fragments) {}
}
