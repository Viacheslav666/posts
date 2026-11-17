package ru.posts.content;

import org.junit.jupiter.api.Test;
import ru.posts.testsupport.architecture.ArchitectureGuard;

class ArchitectureGuardTest {

    @Test
    void moduleBoundaries() {
        ArchitectureGuard.assertPackages("ru.posts.content");
    }
}
