plugins {
    id("posts.java-conventions")
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

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.micrometer:micrometer-core")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":libs:test-support"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
