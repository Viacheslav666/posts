plugins {
    id("posts.spring-app")
    id("posts.integration-test")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:persistence"))
    implementation(project(":libs:messaging"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.aws.s3)
    implementation(libs.tika.core)
    implementation(libs.thumbnailator)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":libs:test-support"))

    "integrationTestImplementation"(libs.spring.boot.starter.test)
    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(platform(libs.testcontainers.bom))
    "integrationTestImplementation"(libs.testcontainers.junit)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"("org.testcontainers:rabbitmq")
    "integrationTestImplementation"("org.testcontainers:localstack")
    "integrationTestImplementation"("org.awaitility:awaitility:4.2.2")
    "integrationTestImplementation"(project(":libs:test-support"))
    "integrationTestRuntimeOnly"(libs.postgresql)
}

// Media needs ffmpeg on PATH — use Temurin+ffmpeg base (see infra/docker/java21-ffmpeg).
val mediaBaseImage =
    providers
        .gradleProperty("postsMediaBaseImage")
        .orElse(
            providers
                .environmentVariable("POSTS_MEDIA_BASE_IMAGE")
                .orElse("docker://posts-java21-ffmpeg:21"),
        )

jib {
    from {
        image = mediaBaseImage.get()
    }
    container {
        user = "65532:65532"
        labels.put("org.opencontainers.image.base.name", mediaBaseImage.get().removePrefix("docker://"))
    }
}
