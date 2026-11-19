pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        // Google CDN mirror of Maven Central — softens 429 storms on shared GH runners.
        maven {
            name = "google-maven-central-mirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "google-maven-central-mirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
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
    "libs:automation-suggest",
    "libs:social-api",
    "libs:social-vk",
    "libs:social-telegram",
    "libs:social-ok",
    "libs:social-max",
    "libs:social-inbound",
    "libs:crosspost",
    "libs:ord-api",
    "libs:ord-vk",
    "libs:ord-yandex",
    "libs:ord-reporting",
    "libs:analytics",
    "libs:yookassa",
    "libs:test-support",
    "apps:api",
    "apps:scheduler",
    "apps:publisher",
    "apps:media",
    "apps:ingest",
    "apps:notifications",
)
