plugins {
    id("posts.spring-app")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(libs.spring.boot.starter.web)
}
