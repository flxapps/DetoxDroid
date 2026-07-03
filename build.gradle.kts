// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
    // DI
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}