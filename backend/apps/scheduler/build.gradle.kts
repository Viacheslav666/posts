plugins {
    id("posts.spring-app")
    id("posts.integration-test")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:persistence"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:security"))
    implementation(project(":libs:yookassa"))
    implementation(project(":libs:ord-api"))
    implementation(project(":libs:ord-vk"))
    implementation(project(":libs:ord-yandex"))
    implementation(project(":libs:ord-reporting"))
    implementation(project(":libs:analytics"))
    implementation(project(":libs:social-api"))
    implementation(project(":libs:social-vk"))
    implementation(project(":libs:social-ok"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.redis)
    implementation("io.micrometer:micrometer-core")
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":libs:test-support"))

    "integrationTestImplementation"(libs.spring.boot.starter.test)
    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(platform(libs.testcontainers.bom))
    "integrationTestImplementation"(libs.testcontainers.junit)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"("org.testcontainers:rabbitmq")
    "integrationTestImplementation"("com.redis:testcontainers-redis:2.2.2")
    "integrationTestImplementation"(project(":libs:test-support"))
    "integrationTestRuntimeOnly"(libs.postgresql)
}
