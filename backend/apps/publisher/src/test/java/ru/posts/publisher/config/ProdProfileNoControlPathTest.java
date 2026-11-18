package ru.posts.publisher.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QA-13: prod profile must force API reconciliation (never emulator {@code /_control}) for VK/OK.
 */
class ProdProfileNoControlPathTest {

    @Test
    @DisplayName("application-prod.yml: reconciliation-mode=api and no /_control")
    void prodProfileForcesApiReconciliation() throws Exception {
        String yaml;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application-prod.yml")) {
            assertThat(in).as("application-prod.yml must exist on classpath").isNotNull();
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(yaml).doesNotContain("/_control");
        assertThat(yaml).contains("reconciliation-mode: api");
        assertThat(yaml).doesNotContain("reconciliation-mode: emulator");
        assertThat(yaml).doesNotContain("localhost:8090");
        // No YAML key may reference the emulator control API path.
        assertThat(yaml.lines().filter(l -> !l.trim().startsWith("#")).anyMatch(l -> l.contains("/_control")))
                .isFalse();
    }
}
