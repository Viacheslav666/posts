plugins {
    id("posts.java-conventions")
}

dependencies {
    api(project(":libs:social-api"))
    api(project(":libs:common"))
    api(project(":libs:ratelimit"))
}
