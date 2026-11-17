package ru.posts.emulator.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PostStoreTest {

    @Test
    void createOrGetIsIdempotent() {
        PostStore store = new PostStore();
        PostStore.CreateResult first = store.createOrGet(Network.VK, "-1", "g", "a", Map.of());
        PostStore.CreateResult second = store.createOrGet(Network.VK, "-1", "g", "b", Map.of());

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.post().remotePostId()).isEqualTo(first.post().remotePostId());
        assertThat(store.verify().ok()).isTrue();
        assertThat(store.verify().duplicateAttemptsByNetwork().get("vk")).isEqualTo(1L);
    }
}
