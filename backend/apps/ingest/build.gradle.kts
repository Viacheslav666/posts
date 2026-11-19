plugins {
    id("posts.spring-app")
    id("posts.integration-test")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:persistence"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:grabber"))
    implementation(project(":libs:automation-suggest"))
    implementation(project(":libs:social-inbound"))
    implementation(project(":libs:social-vk"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.spring.boot.starter.actuator)
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
    "integrationTestImplementation"("org.wiremock:wiremock-standalone:3.10.0")
    "integrationTestImplementation"(project(":libs:test-support"))
    "integrationTestRuntimeOnly"(libs.postgresql)
}
