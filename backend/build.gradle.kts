plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

tasks.register("integrationTest") {
    group = "verification"
    description = "Runs integration tests in all subprojects"
    dependsOn(subprojects.mapNotNull { sub ->
        sub.tasks.findByName("integrationTest")
    })
}
