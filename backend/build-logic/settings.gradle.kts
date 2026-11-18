dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = "google-maven-central-mirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
