plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.cloud.tools.jib") version "3.4.4"
    id("com.diffplug.spotless") version "7.0.2"
}

group = "ru.posts"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Align with DEV-3 / posts.spring-app when Trivy reports CRITICAL Tomcat CVEs.
extra["tomcat.version"] = "10.1.62"

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat("2.50.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}

val imageName = "posts-social-emulator"
val defaultRegistryImage = "ghcr.io/viacheslav666/$imageName"
val baseImage = "gcr.io/distroless/java21-debian12:nonroot"

val imageTags: Set<String> =
    System
        .getenv("IMAGE_TAGS")
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
        image = baseImage
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
        ports = listOf("8090")
        creationTime = "USE_CURRENT_TIMESTAMP"
        user = "nonroot:nonroot"
        labels.set(
            mapOf(
                "org.opencontainers.image.title" to imageName,
                "org.opencontainers.image.description" to "Posts SMM platform — social networks emulator (QA-3)",
                "org.opencontainers.image.source" to "https://github.com/Viacheslav666/posts",
                "org.opencontainers.image.url" to "https://github.com/Viacheslav666/posts",
                "org.opencontainers.image.revision" to gitRevision,
                "org.opencontainers.image.created" to imageCreated,
                "org.opencontainers.image.licenses" to "Proprietary",
                "org.opencontainers.image.vendor" to "Posts",
                "org.opencontainers.image.base.name" to baseImage,
            ),
        )
    }
}
