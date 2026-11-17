package ru.posts.security;

import org.junit.jupiter.api.Test;
import ru.posts.testsupport.architecture.ArchitectureGuard;

class ArchitectureGuardTest {

    @Test
    void moduleBoundaries() {
        ArchitectureGuard.assertPackages("ru.posts.security");
    }
}
