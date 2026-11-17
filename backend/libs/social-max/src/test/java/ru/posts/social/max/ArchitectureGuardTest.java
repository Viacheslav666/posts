package ru.posts.social.max;

import org.junit.jupiter.api.Test;
import ru.posts.testsupport.architecture.ArchitectureGuard;

class ArchitectureGuardTest {

    @Test
    void moduleBoundaries() {
        ArchitectureGuard.assertPackages("ru.posts.social.max");
    }
}
