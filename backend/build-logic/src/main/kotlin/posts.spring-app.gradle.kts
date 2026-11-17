plugins {
    id("posts.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Harden embedded Tomcat beyond the Boot BOM when Trivy reports CRITICAL with fixes.
extra["tomcat.version"] = "10.1.62"

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveClassifier.set("")
}

// Keep a plain jar so other modules (e.g. ArchUnit via test-support) can depend on apps.
tasks.named<Jar>("jar") {
    enabled = true
}

val imageName = "posts-${project.name}"
val defaultRegistryImage = "ghcr.io/viacheslav666/$imageName"

val imageTags: Set<String> =
    System.getenv("IMAGE_TAGS")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        .orEmpty()
        .ifEmpty { setOf("latest") }

val gitRevision: String =
    System.getenv("GITHUB_SHA")
        ?: System.getenv("IMAGE_REVISION")
        ?: "local"

val imageCreated: String =
    System.getenv("IMAGE_CREATED")
        ?: System.currentTimeMillis().toString()

jib {
    from {
        image = "gcr.io/distroless/java21-debian12:nonroot"
    }
    to {
        image = System.getenv("IMAGE_NAME") ?: defaultRegistryImage
        tags = imageTags
    }
    container {
        jvmFlags =
            listOf(
                "-XX:MaxRAMPercentage=75",
                "-XX:+ExitOnOutOfMemoryError",
            )
        ports = listOf("8080")
        creationTime = "USE_CURRENT_TIMESTAMP"
        user = "nonroot:nonroot"
        labels.set(
            mapOf(
                "org.opencontainers.image.title" to imageName,
                "org.opencontainers.image.description" to "Posts SMM platform — $imageName",
                "org.opencontainers.image.source" to "https://github.com/Viacheslav666/posts",
                "org.opencontainers.image.url" to "https://github.com/Viacheslav666/posts",
                "org.opencontainers.image.revision" to gitRevision,
                "org.opencontainers.image.created" to imageCreated,
                "org.opencontainers.image.licenses" to "Proprietary",
                "org.opencontainers.image.vendor" to "Posts",
                "org.opencontainers.image.base.name" to "gcr.io/distroless/java21-debian12:nonroot",
            ),
        )
    }
}
