package ru.posts.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublisherApplicationTests {

    @Test
    void applicationEntryPointIsPresent() {
        assertThat(PublisherApplication.class.getName()).contains("PublisherApplication");
    }
}
