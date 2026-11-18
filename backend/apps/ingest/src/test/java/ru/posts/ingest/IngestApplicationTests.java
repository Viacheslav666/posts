package ru.posts.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IngestApplicationTests {

    @Test
    void applicationEntryPointIsPresent() {
        assertThat(IngestApplication.class.getName()).contains("IngestApplication");
    }
}
