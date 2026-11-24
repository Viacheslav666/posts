package ru.posts.grabber.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .containsExactly("-10", "-20", "-12345");
    }

    @Test
    void negatesBareCommunityIdAndKeepsScreenName() {
        assertThat(SourceIdNormalizer.normalizeAll(List.of("12345", "https://vk.com/team", "-8")))
                .containsExactly("-12345", "team", "-8");
    }

    @Test
    void invalidSourceDoesNotEchoToken() {
        String token = "vk1.a." + "secretTOKEN".repeat(6);
        assertThatThrownBy(() -> SourceIdNormalizer.normalizeOne(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Некорректный ID источника")
                .hasMessageNotContaining(token);
    }
}
