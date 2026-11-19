package ru.posts.social.inbound.album;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Aggregates Telegram {@code media_group_id} updates into one POST_NEW within a 3s window.
 * In-memory by default (single instance / tests); Redis-backed variant can wrap the same API.
 */
public class TelegramAlbumAggregator {

    public static final Duration WINDOW = Duration.ofSeconds(3);

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, AlbumBuffer> buffers = new ConcurrentHashMap<>();

    public TelegramAlbumAggregator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void buffer(String mediaGroupId, String channelKey, String botId, JsonNode updateJson) {
        String key = channelKey + ":" + mediaGroupId;
        buffers.compute(key, (k, existing) -> {
            AlbumBuffer buf = existing == null ? new AlbumBuffer(Instant.now().plus(WINDOW), botId) : existing;
            buf.fragments.add(copy(updateJson));
            return buf;
        });
    }

    /** Returns and removes albums whose window has elapsed. */
    public List<ReadyAlbum> drainReady(Instant now) {
        List<ReadyAlbum> ready = new ArrayList<>();
        for (Map.Entry<String, AlbumBuffer> e : buffers.entrySet()) {
            if (!e.getValue().readyAt.isAfter(now)) {
                AlbumBuffer removed = buffers.remove(e.getKey());
                if (removed != null && !removed.fragments.isEmpty()) {
                    String[] parts = e.getKey().split(":", 2);
                    ready.add(new ReadyAlbum(
                            parts[0], parts.length > 1 ? parts[1] : "", removed.botId, List.copyOf(removed.fragments)));
                }
            }
        }
        return ready;
    }

    /** Test helper: force-drain a specific album key immediately. */
    public Optional<List<JsonNode>> forceDrain(String mediaGroupId, String channelKey) {
        AlbumBuffer removed = buffers.remove(channelKey + ":" + mediaGroupId);
        if (removed == null || removed.fragments.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(removed.fragments));
    }

    public int pendingCount() {
        return buffers.size();
    }

    private JsonNode copy(JsonNode node) {
        try {
            return objectMapper.readTree(objectMapper.writeValueAsBytes(node));
        } catch (java.io.IOException e) {
            return node;
        }
    }

    private static final class AlbumBuffer {
        private final Instant readyAt;
        private final String botId;
        private final CopyOnWriteArrayList<JsonNode> fragments = new CopyOnWriteArrayList<>();

        private AlbumBuffer(Instant readyAt, String botId) {
            this.readyAt = readyAt;
            this.botId = botId;
        }
    }

    public record ReadyAlbum(String channelKey, String mediaGroupId, String botId, List<JsonNode> fragments) {}
}
