package ru.posts.antispam;

import org.junit.jupiter.api.Test;
import ru.posts.testsupport.architecture.ArchitectureGuard;

class ArchitectureGuardTest {

    @Test
    void packages() {
        ArchitectureGuard.assertPackages("ru.posts.antispam");
    }
}
