package ru.posts.social.inbound.album;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single-JVM album buffer. Silence timer resets on every fragment so a partial flush cannot win
 * before the group goes quiet. Production uses {@link JdbcAlbumFragmentStore}.
 */
public class InMemoryAlbumFragmentStore implements AlbumFragmentStore {

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Buffer> buffers = new ConcurrentHashMap<>();

    public InMemoryAlbumFragmentStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void add(String channelKey, String mediaGroupId, String botId, JsonNode update, Instant receivedAt) {
        String key = channelKey + ":" + mediaGroupId;
        Instant quietAt = receivedAt.plus(TelegramAlbumAggregator.WINDOW);
        buffers.compute(key, (k, existing) -> {
            Buffer buf = existing == null ? new Buffer(botId) : existing;
            buf.quietAt = quietAt;
            buf.fragments.add(copy(update));
            return buf;
        });
    }

    @Override
    public List<TelegramAlbumAggregator.ReadyAlbum> drainReady(Instant now) {
        List<TelegramAlbumAggregator.ReadyAlbum> ready = new ArrayList<>();
        for (var entry : buffers.entrySet()) {
            if (entry.getValue().quietAt.isAfter(now)) {
                continue;
            }
            Buffer removed = buffers.remove(entry.getKey());
            if (removed == null || removed.fragments.isEmpty()) {
                continue;
            }
            String[] parts = entry.getKey().split(":", 2);
            ready.add(new TelegramAlbumAggregator.ReadyAlbum(
                    parts[0], parts.length > 1 ? parts[1] : "", removed.botId, List.copyOf(removed.fragments)));
        }
        return ready;
    }

    int pendingCount() {
        return buffers.size();
    }

    private JsonNode copy(JsonNode node) {
        try {
            return objectMapper.readTree(objectMapper.writeValueAsBytes(node));
        } catch (java.io.IOException e) {
            return node;
        }
    }

    private static final class Buffer {
        private final String botId;
        private final CopyOnWriteArrayList<JsonNode> fragments = new CopyOnWriteArrayList<>();
        private Instant quietAt;

        private Buffer(String botId) {
            this.botId = botId;
        }
    }
}
