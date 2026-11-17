plugins {
    id("posts.java-conventions")
}

dependencies {
    api(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    api("org.testcontainers:junit-jupiter")
    api("org.testcontainers:postgresql")
    api("org.springframework.boot:spring-boot-testcontainers:3.4.2")
    api(project(":libs:common"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}
