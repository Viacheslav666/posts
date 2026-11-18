package ru.posts.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MediaProcessorMimeTest {

    @Test
    void rejectsSpoofedVideoMimeForImageBytes() {
        assertThat(MediaProcessor.mimeMatchesKind("image/jpeg", "video", "video/mp4"))
                .isFalse();
    }

    @Test
    void acceptsMatchingImage() {
        assertThat(MediaProcessor.mimeMatchesKind("image/png", "image", "image/png"))
                .isTrue();
    }

    @Test
    void acceptsMatchingVideo() {
        assertThat(MediaProcessor.mimeMatchesKind("video/mp4", "video", "video/mp4"))
                .isTrue();
    }
}
