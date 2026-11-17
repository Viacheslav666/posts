import java.net.URI
import java.util.concurrent.TimeUnit

plugins {
    id("posts.spring-app")
    id("posts.integration-test")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:domain"))
    implementation(project(":libs:persistence"))
    implementation(project(":libs:security"))
    implementation(project(":libs:ratelimit"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.h2)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(project(":libs:test-support"))

    "integrationTestImplementation"(libs.spring.boot.starter.test)
    "integrationTestImplementation"(libs.spring.security.test)
    "integrationTestImplementation"(libs.spring.boot.testcontainers)
    "integrationTestImplementation"(platform(libs.testcontainers.bom))
    "integrationTestImplementation"(libs.testcontainers.junit)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"(project(":libs:test-support"))
    "integrationTestRuntimeOnly"(libs.postgresql)
}

// Custom OpenAPI export (ADR-0008): springdoc-openapi-gradle-plugin 1.9 has
// Gradle 8.12 implicit-dependency issues with multi-module jars; we boot the fat
// jar ourselves and fetch /v3/api-docs.yaml.
val openApiOutput =
    rootProject.layout.projectDirectory
        .file("../docs/api/openapi.yaml")
        .asFile

tasks.register("generateOpenApiDocs") {
    group = "documentation"
    description = "Starts api with profile openapi and writes docs/api/openapi.yaml"
    dependsOn("bootJar")
    outputs.file(openApiOutput)

    doLast {
        val bootJar = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").get()
        val jarFile = bootJar.archiveFile.get().asFile
        val port = "18080"
        val process =
            ProcessBuilder(
                javaLauncher
                    .get()
                    .executablePath.asFile.absolutePath,
                "-jar",
                jarFile.absolutePath,
                "--spring.profiles.active=openapi",
                "--server.port=$port",
            ).redirectErrorStream(true)
                .directory(project.projectDir)
                .start()

        try {
            val url = URI("http://127.0.0.1:$port/v3/api-docs.yaml").toURL()
            var lastError: Exception? = null
            var yaml: String? = null
            repeat(60) {
                if (!process.isAlive) {
                    throw GradleException(
                        "API exited before OpenAPI became available:\n" +
                            process.inputStream.bufferedReader().readText(),
                    )
                }
                try {
                    yaml = url.openStream().bufferedReader().readText()
                    return@repeat
                } catch (ex: Exception) {
                    lastError = ex
                    Thread.sleep(1000)
                }
            }
            if (yaml == null) {
                throw GradleException("Timed out waiting for OpenAPI docs", lastError)
            }
            openApiOutput.parentFile.mkdirs()
            openApiOutput.writeText(yaml!!)
            logger.lifecycle("Wrote OpenAPI to ${openApiOutput.absolutePath}")
        } finally {
            process.destroy()
            process.waitFor(10, TimeUnit.SECONDS)
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }
}

val javaLauncher =
    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
