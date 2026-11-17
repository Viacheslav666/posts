plugins {
    id("posts.java-conventions")
}

dependencies {
    // Keep S3 off consumer compile/runtime classpaths; only api/media pull it in.
    implementation(libs.aws.s3)
}
