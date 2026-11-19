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
    api(project(":libs:messaging"))
    api(project(":libs:security"))
    api(project(":libs:social-inbound"))
    api(project(":libs:social-api"))
    api(project(":libs:content"))

    implementation(project(":libs:social-vk"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.micrometer:micrometer-core")
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.16.0")
    // Aho-Corasick for keyword filters (BE-34); cached by config_version.
    implementation("org.ahocorasick:ahocorasick:0.6.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":libs:test-support"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
