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
    api(project(":libs:common"))

    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api(libs.nimbus.jose.jwt)
    api(libs.totp)
    api("org.bouncycastle:bcprov-jdk18on:1.79")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
