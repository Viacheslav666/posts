package ru.posts.testsupport.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Entry points for module-level ArchUnit tests. Every backend module wires {@code test-support} and
 * calls these helpers from its own test source set.
 */
public final class ArchitectureGuard {

    private ArchitectureGuard() {}

    /** Full cross-module suite (requires all module {@code classes} tasks to have run). */
    public static void assertModuleBoundaries() {
        JavaClasses classes = ModuleClasspath.importProductionClasses();
        assertThat(classes).as("production classes for ArchUnit").isNotEmpty();
        PostsArchitectureRules.checkAll(classes);
    }

    /** Package-local check used by individual modules (fast, rules relevant to the package). */
    public static void assertPackages(String... packages) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(packages);
        assertThat(classes).as("classes in %s", (Object) packages).isNotEmpty();
        PostsArchitectureRules.checkRelevant(classes, packages);
    }
}
