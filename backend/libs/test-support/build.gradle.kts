plugins {
    id("posts.java-conventions")
}

dependencies {
    api(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    api("org.testcontainers:junit-jupiter")
    api("org.testcontainers:postgresql")
    api("org.springframework.boot:spring-boot-testcontainers:3.4.2")
    api(project(":libs:common"))
    api(libs.archunit.junit5)
    api(libs.assertj.core)
}

val moduleClassProjects =
    listOf(
        ":libs:common",
        ":libs:domain",
        ":libs:persistence",
        ":libs:messaging",
        ":libs:security",
        ":libs:ratelimit",
        ":libs:content",
        ":libs:social-api",
        ":libs:social-vk",
        ":libs:social-telegram",
        ":libs:social-ok",
        ":libs:social-max",
        ":libs:ord-api",
        ":libs:ord-vk",
        ":libs:ord-yandex",
        ":apps:api",
        ":apps:scheduler",
        ":apps:publisher",
        ":apps:media",
        ":apps:ingest",
        ":apps:notifications",
    )

tasks.test {
    dependsOn(moduleClassProjects.map { "$it:classes" })
    systemProperty("posts.backend.root", rootProject.projectDir.absolutePath)
}
