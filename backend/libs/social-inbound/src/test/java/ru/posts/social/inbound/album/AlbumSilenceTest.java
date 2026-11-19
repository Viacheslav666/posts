package ru.posts.social.inbound.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlbumSilenceTest {

    @Test
    void sharedStoreKeepsFragmentsUntilTheGroupGoesQuiet() {
        InMemoryAlbumFragmentStore store = new InMemoryAlbumFragmentStore(new ObjectMapper());
        TelegramAlbumAggregator first = new TelegramAlbumAggregator(store);
        TelegramAlbumAggregator second = new TelegramAlbumAggregator(store);
        Instant t0 = Instant.parse("2026-11-19T12:00:00Z");
        store.add("channel", "group-1", "bot", update(1), t0);
        store.add("channel", "group-1", "bot", update(2), t0.plusSeconds(2));

        assertThat(first.drainReady(t0.plusSeconds(3))).isEmpty();
        List<TelegramAlbumAggregator.ReadyAlbum> ready = second.drainReady(t0.plusSeconds(6));
        assertThat(ready).hasSize(1);
        assertThat(ready.getFirst().fragments()).hasSize(2);
    }

    private static ObjectNode update(int id) {
        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("update_id", id);
        return node;
    }
}
