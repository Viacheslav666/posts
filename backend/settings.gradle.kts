pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "posts-backend"

include(
    "libs:common",
    "libs:domain",
    "libs:persistence",
    "libs:messaging",
    "libs:security",
    "libs:ratelimit",
    "libs:content",
    "libs:grabber",
    "libs:social-api",
    "libs:social-vk",
    "libs:social-telegram",
    "libs:social-ok",
    "libs:social-max",
    "libs:ord-api",
    "libs:ord-vk",
    "libs:ord-yandex",
    "libs:test-support",
    "apps:api",
    "apps:scheduler",
    "apps:publisher",
    "apps:media",
    "apps:ingest",
    "apps:notifications",
)
