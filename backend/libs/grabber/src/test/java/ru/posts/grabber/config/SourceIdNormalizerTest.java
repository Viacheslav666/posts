package ru.posts.grabber.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SourceIdNormalizerTest {

    @Test
    void normalizesSpacesAndScreenNames() {
        assertThat(SourceIdNormalizer.normalizeAll(List.of(" -123, club456 , @public ")))
                .containsExactly("-123", "-456", "public");
    }

    @Test
    void normalizesClubPublicAndVkUrls() {
        assertThat(SourceIdNormalizer.normalizeAll(
                        List.of("https://vk.com/club10", "vk.com/public20/", "https://vk.ru/club10?from=x", "12345")))
                .containsExactly("-10", "-20", "12345");
    }
}
