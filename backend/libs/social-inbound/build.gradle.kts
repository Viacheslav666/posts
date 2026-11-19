plugins {
    id("posts.java-conventions")
    id("posts.integration-test")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.16")
    }
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    api(project(":libs:common"))
    api(project(":libs:persistence"))
    api(project(":libs:messaging"))
    api(project(":libs:security"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.micrometer:micrometer-core")
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.16.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":libs:test-support"))

    "integrationTestImplementation"("org.springframework.boot:spring-boot-starter-test")
    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(platform(libs.testcontainers.bom))
    "integrationTestImplementation"(libs.testcontainers.junit)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"(libs.flyway.core)
    "integrationTestImplementation"(libs.flyway.database.postgresql)
    "integrationTestRuntimeOnly"(libs.postgresql)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
