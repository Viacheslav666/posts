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
    api(project(":libs:ord-api"))
    api(project(":libs:social-api"))
    api(project(":libs:security"))

    implementation(project(":libs:messaging"))
    implementation(project(":libs:ord-vk"))
    implementation(project(":libs:ord-yandex"))
    implementation(project(":libs:social-vk"))
    implementation(project(":libs:social-ok"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.micrometer:micrometer-core")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
