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
    api(project(":libs:ord-api"))
    api(project(":libs:common"))

    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.wiremock)
}
