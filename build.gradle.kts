// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Optional private plugin classpath: each non-comment line of the (gitignored) file is a Gradle
// plugin coordinate added to the root buildscript, which every subproject inherits. Public clones
// have no such file and build unchanged; see the apply(from:) hook in app/build.gradle.kts.
buildscript {
    val privatePlugins = file("private-plugins.txt")
    if (privatePlugins.exists()) {
        repositories {
            gradlePluginPortal()
        }
        dependencies {
            privatePlugins.readLines()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .forEach { classpath(it) }
        }
    }
}

plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
    // DI
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
