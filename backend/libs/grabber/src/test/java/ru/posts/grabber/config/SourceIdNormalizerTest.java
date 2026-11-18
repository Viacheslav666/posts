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
}
