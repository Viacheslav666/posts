plugins {
    id("posts.spring-app")
    id("posts.integration-test")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:domain"))
    implementation(project(":libs:persistence"))
    implementation(project(":libs:messaging"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
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
    "integrationTestImplementation"(libs.wiremock)
    "integrationTestImplementation"(libs.greenmail.junit5)
    "integrationTestImplementation"(project(":libs:test-support"))
    "integrationTestRuntimeOnly"(libs.postgresql)
}

tasks.named<Test>("integrationTest") {
    systemProperty("posts.repo.root", rootProject.projectDir.parentFile.absolutePath)
}
