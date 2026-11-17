package ru.posts.common;

import org.junit.jupiter.api.Test;
import ru.posts.testsupport.architecture.ArchitectureGuard;

class ArchitectureGuardTest {

    @Test
    void moduleBoundaries() {
        ArchitectureGuard.assertPackages("ru.posts.common");
    }
}
