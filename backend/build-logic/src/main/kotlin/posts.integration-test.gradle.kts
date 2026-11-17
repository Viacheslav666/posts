plugins {
    id("posts.java-conventions")
}

val integrationTest by sourceSets.creating {
    compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
    runtimeClasspath += output + compileClasspath
}

configurations[integrationTest.implementationConfigurationName]
    .extendsFrom(configurations["testImplementation"])
configurations[integrationTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations["testRuntimeOnly"])

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests"
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.named("check") {
    dependsOn(integrationTestTask)
}
