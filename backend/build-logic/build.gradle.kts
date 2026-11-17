plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugins.spring.boot.toDep())
    implementation(libs.plugins.spring.dependency.management.toDep())
    implementation(libs.plugins.jib.toDep())
    implementation(libs.plugins.spotless.toDep())
    implementation(libs.plugins.errorprone.toDep())
}

fun Provider<PluginDependency>.toDep() =
    map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
