package ru.posts.testsupport.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Imports production class files from all backend modules for cross-module ArchUnit checks. */
public final class ModuleClasspath {

    private static final String[] MODULE_CLASS_DIRS = {
        "libs/common/build/classes/java/main",
        "libs/domain/build/classes/java/main",
        "libs/persistence/build/classes/java/main",
        "libs/messaging/build/classes/java/main",
        "libs/security/build/classes/java/main",
        "libs/ratelimit/build/classes/java/main",
        "libs/content/build/classes/java/main",
        "libs/social-api/build/classes/java/main",
        "libs/social-vk/build/classes/java/main",
        "libs/social-telegram/build/classes/java/main",
        "libs/social-ok/build/classes/java/main",
        "libs/social-max/build/classes/java/main",
        "libs/ord-api/build/classes/java/main",
        "libs/ord-vk/build/classes/java/main",
        "libs/ord-yandex/build/classes/java/main",
        "libs/yookassa/build/classes/java/main",
        "apps/api/build/classes/java/main",
        "apps/scheduler/build/classes/java/main",
        "apps/publisher/build/classes/java/main",
        "apps/media/build/classes/java/main",
        "apps/ingest/build/classes/java/main",
        "apps/notifications/build/classes/java/main"
    };

    private ModuleClasspath() {}

    public static JavaClasses importProductionClasses() {
        Path backendRoot = Paths.get(System.getProperty("posts.backend.root", "../.."))
                .toAbsolutePath()
                .normalize();

        List<Path> classDirs = new ArrayList<>();
        for (String relative : MODULE_CLASS_DIRS) {
            Path dir = backendRoot.resolve(relative);
            if (dir.toFile().isDirectory()) {
                classDirs.add(dir);
            }
        }

        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPaths(classDirs);
    }
}
