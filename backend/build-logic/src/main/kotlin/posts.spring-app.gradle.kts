plugins {
    id("posts.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.4.2"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveClassifier.set("")
}

// Keep a plain jar so other modules (e.g. architecture tests) can depend on apps.
tasks.named<Jar>("jar") {
    enabled = true
}

jib {
    from {
        image = "gcr.io/distroless/java21-debian12:nonroot"
    }
    to {
        image = "ghcr.io/viacheslav666/posts-${project.name}"
        tags = setOf("latest")
    }
    container {
        jvmFlags = listOf(
            "-XX:MaxRAMPercentage=75.0",
            "-XX:+ExitOnOutOfMemoryError",
        )
        ports = listOf("8080")
        creationTime = "USE_CURRENT_TIMESTAMP"
        labels.set(
            mapOf(
                "org.opencontainers.image.title" to "posts-${project.name}",
                "org.opencontainers.image.source" to "https://github.com/Viacheslav666/posts",
            ),
        )
        user = "nonroot:nonroot"
    }
}
