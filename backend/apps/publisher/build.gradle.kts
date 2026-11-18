plugins {
    id("posts.spring-app")
    id("posts.integration-test")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:persistence"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:ratelimit"))
    implementation(project(":libs:content"))
    implementation(project(":libs:social-api"))
    implementation(project(":libs:social-telegram"))
    implementation(project(":libs:social-vk"))
    implementation(project(":libs:social-ok"))
    implementation(project(":libs:social-max"))
    implementation(project(":libs:security"))
    implementation(project(":libs:ord-api"))
    implementation(libs.aws.s3)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.resilience4j.spring.boot3)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":libs:test-support"))

    "integrationTestImplementation"(libs.spring.boot.starter.test)
    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(platform(libs.testcontainers.bom))
    "integrationTestImplementation"(libs.testcontainers.junit)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"("org.testcontainers:rabbitmq")
    "integrationTestImplementation"("org.awaitility:awaitility:4.2.2")
    "integrationTestImplementation"(project(":libs:test-support"))
    "integrationTestRuntimeOnly"(libs.postgresql)
}

tasks.named<Test>("integrationTest") {
    systemProperty("posts.repo.root", rootProject.projectDir.parentFile.absolutePath)
    // Fast TC-PUB suite in CI; heavy load (@Tag("nightly")) runs via -PincludeNightly=true.
    useJUnitPlatform {
        if (!project.hasProperty("includeNightly")) {
            excludeTags("nightly")
        }
    }
}
