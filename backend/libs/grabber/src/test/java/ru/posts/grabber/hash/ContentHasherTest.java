package ru.posts.grabber.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContentHasherTest {

    @Test
    void stableForNormalizedTextAndSortedMedia() {
        String a = ContentHasher.hash("Hello   world", List.of("bb", "aa"), List.of("photo1"));
        String b = ContentHasher.hash("Hello world", List.of("aa", "bb"), List.of("photo1"));
        assertThat(a).isEqualTo(b).hasSize(64);
    }
}
