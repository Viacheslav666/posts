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

    implementation(libs.bucket4j.core)
    implementation(libs.bucket4j.redis)
    implementation(libs.resilience4j.spring.boot3)
    implementation("io.lettuce:lettuce-core")
    implementation("io.micrometer:micrometer-core")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.assertj.core)
}
