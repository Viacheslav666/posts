package ru.posts.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MediaApplicationTests {

    @Test
    void applicationEntryPointIsPresent() {
        assertThat(MediaApplication.class.getName()).contains("MediaApplication");
    }
}
