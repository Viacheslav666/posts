plugins {
    id("posts.spring-app")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(libs.spring.boot.starter.web)
}

// Media needs ffmpeg on PATH — use Temurin+ffmpeg base (see infra/docker/java21-ffmpeg).
// Local: docker build -t posts-java21-ffmpeg:21 infra/docker/java21-ffmpeg
// Override: -PpostsMediaBaseImage=docker://other:tag or registry ref without docker://
val mediaBaseImage =
    providers
        .gradleProperty("postsMediaBaseImage")
        .orElse(
            providers
                .environmentVariable("POSTS_MEDIA_BASE_IMAGE")
                .orElse("docker://posts-java21-ffmpeg:21"),
        )

jib {
    from {
        image = mediaBaseImage.get()
    }
    container {
        user = "65532:65532"
        labels.put("org.opencontainers.image.base.name", mediaBaseImage.get().removePrefix("docker://"))
    }
}
