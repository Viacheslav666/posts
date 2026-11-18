package ru.posts.analytics;

import org.junit.jupiter.api.Test;
import ru.posts.testsupport.architecture.ArchitectureGuard;

class ArchitectureGuardTest {

    @Test
    void analyticsModuleBoundaries() {
        ArchitectureGuard.assertPackages("ru.posts.analytics");
    }
}
