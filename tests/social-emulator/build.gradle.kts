plugins {
    java
    id("org.springframework.boot") version "3.4.2"
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

jib {
    // Temurin (not distroless): QA image needs a shell for compose healthchecks.
    from {
        image = "eclipse-temurin:21-jre-jammy"
    }
    to {
        image = "ghcr.io/viacheslav666/posts-social-emulator"
        tags = setOf("latest")
    }
    container {
        jvmFlags =
            listOf(
                "-XX:MaxRAMPercentage=75.0",
                "-XX:+ExitOnOutOfMemoryError",
            )
        ports = listOf("8090")
        creationTime = "USE_CURRENT_TIMESTAMP"
        labels.set(
            mapOf(
                "org.opencontainers.image.title" to "posts-social-emulator",
                "org.opencontainers.image.source" to "https://github.com/Viacheslav666/posts",
            ),
        )
        user = "1000:1000"
    }
}
