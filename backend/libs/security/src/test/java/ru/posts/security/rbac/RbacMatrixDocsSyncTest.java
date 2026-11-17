package ru.posts.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QA-6: classpath-копия матрицы (BE-6) должна совпадать с docs/product/roles-permissions.yaml.
 */
class RbacMatrixDocsSyncTest {

    @Test
    @DisplayName("docs/product/roles-permissions.yaml ↔ classpath rbac/roles-permissions.yaml")
    void productYamlMatchesClasspathCopy() throws IOException {
        Path docs = findRepoRoot().resolve("docs/product/roles-permissions.yaml");
        assertThat(docs).exists();
        String product = normalize(Files.readString(docs, StandardCharsets.UTF_8));

        try (InputStream in =
                RbacMatrixLoader.class.getClassLoader().getResourceAsStream(RbacMatrixLoader.DEFAULT_CLASSPATH)) {
            assertThat(in)
                    .as("classpath %s", RbacMatrixLoader.DEFAULT_CLASSPATH)
                    .isNotNull();
            String classpath = normalize(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(classpath)
                    .as("Update backend/libs/security/.../rbac/roles-permissions.yaml from docs/product/")
                    .isEqualTo(product);
        }
    }

    private static String normalize(String raw) {
        return raw.replace("\r\n", "\n").trim() + "\n";
    }

    private static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 10; i++) {
            if (Files.isDirectory(dir.resolve("docs/product")) && Files.isDirectory(dir.resolve("backend"))) {
                return dir;
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        Path fromModule = Path.of("../../..").toAbsolutePath().normalize();
        if (Files.isDirectory(fromModule.resolve("docs/product"))) {
            return fromModule;
        }
        throw new IllegalStateException(
                "Cannot locate repo root from " + Path.of("").toAbsolutePath());
    }
}
