package ru.posts.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModuleArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        Path backendRoot = Paths.get(System.getProperty("posts.backend.root", "../.."))
                .toAbsolutePath()
                .normalize();

        List<Path> classDirs = List.of(
                backendRoot.resolve("libs/common/build/classes/java/main"),
                backendRoot.resolve("libs/domain/build/classes/java/main"),
                backendRoot.resolve("libs/persistence/build/classes/java/main"),
                backendRoot.resolve("libs/messaging/build/classes/java/main"),
                backendRoot.resolve("libs/security/build/classes/java/main"),
                backendRoot.resolve("libs/ratelimit/build/classes/java/main"),
                backendRoot.resolve("libs/content/build/classes/java/main"),
                backendRoot.resolve("libs/social-api/build/classes/java/main"),
                backendRoot.resolve("libs/social-vk/build/classes/java/main"),
                backendRoot.resolve("libs/social-telegram/build/classes/java/main"),
                backendRoot.resolve("libs/social-ok/build/classes/java/main"),
                backendRoot.resolve("libs/social-max/build/classes/java/main"),
                backendRoot.resolve("apps/api/build/classes/java/main"),
                backendRoot.resolve("apps/scheduler/build/classes/java/main"),
                backendRoot.resolve("apps/publisher/build/classes/java/main"),
                backendRoot.resolve("apps/media/build/classes/java/main"),
                backendRoot.resolve("apps/ingest/build/classes/java/main"),
                backendRoot.resolve("apps/notifications/build/classes/java/main"));

        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPaths(classDirs);

        assertThat(classes).isNotEmpty();
    }

    @Test
    void appsMustNotDependOnOtherApps() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("ru.posts.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "ru.posts.scheduler..",
                        "ru.posts.publisher..",
                        "ru.posts.media..",
                        "ru.posts.ingest..",
                        "ru.posts.notifications..")
                .because("apps must not depend on each other");
        rule.check(classes);

        noClasses()
                .that()
                .resideInAPackage("ru.posts.scheduler..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "ru.posts.api..",
                        "ru.posts.publisher..",
                        "ru.posts.media..",
                        "ru.posts.ingest..",
                        "ru.posts.notifications..")
                .check(classes);
    }

    @Test
    void domainMustNotDependOnApps() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("ru.posts.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "ru.posts.api..",
                        "ru.posts.scheduler..",
                        "ru.posts.publisher..",
                        "ru.posts.media..",
                        "ru.posts.ingest..",
                        "ru.posts.notifications..")
                .because("domain must not depend on apps");
        rule.check(classes);
    }
}
