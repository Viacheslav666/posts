package ru.posts.testsupport.architecture;

import org.junit.jupiter.api.Test;

class ModuleArchitectureTest {

    @Test
    void moduleBoundariesFromSpec() {
        ArchitectureGuard.assertModuleBoundaries();
    }
}
