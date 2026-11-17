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
    "libs:social-api",
    "libs:social-vk",
    "libs:social-telegram",
    "libs:social-ok",
    "libs:social-max",
    "libs:test-support",
    "apps:api",
    "apps:scheduler",
    "apps:publisher",
    "apps:media",
    "apps:ingest",
    "apps:notifications",
)
