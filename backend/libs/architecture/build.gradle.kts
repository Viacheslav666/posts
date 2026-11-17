plugins {
    id("posts.java-conventions")
}

dependencies {
    testImplementation(project(":libs:common"))
    testImplementation(project(":libs:domain"))
    testImplementation(project(":libs:persistence"))
    testImplementation(project(":libs:messaging"))
    testImplementation(project(":libs:security"))
    testImplementation(project(":libs:ratelimit"))
    testImplementation(project(":libs:content"))
    testImplementation(project(":libs:social-api"))
    testImplementation(project(":libs:social-vk"))
    testImplementation(project(":libs:social-telegram"))
    testImplementation(project(":libs:social-ok"))
    testImplementation(project(":libs:social-max"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

val appProjects =
    listOf(
        ":apps:api",
        ":apps:scheduler",
        ":apps:publisher",
        ":apps:media",
        ":apps:ingest",
        ":apps:notifications",
    )

tasks.test {
    dependsOn(appProjects.map { "$it:classes" })
    systemProperty("posts.backend.root", rootProject.projectDir.absolutePath)
}
