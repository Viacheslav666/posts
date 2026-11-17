plugins {
    id("posts.java-conventions")
}

dependencies {
    api(project(":libs:common"))
    api(libs.bucket4j.core)

    testImplementation(libs.assertj.core)
}
