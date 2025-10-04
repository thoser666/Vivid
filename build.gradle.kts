// Top-level build file where you can add configuration options common to all sub-projects/modules.
val compileSdkVersion by extra(36)
val minSdkVersion by extra(26)
val targetSdkVersion by extra(36)

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false // Corrected Hilt plugin ID
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.library) apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
}

// It's good practice to declare repositories that host your plugins
// within a pluginManagement block in settings.gradle.kts,
// but for the buildscript block, ensure Google and MavenCentral are present.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Classpath for AGP, Kotlin, and Hilt are typically handled by the plugins block
        // when using the modern syntax.
        // If you still need to declare them here for some reason:
        // classpath("com.android.tools.build:gradle:8.0.0")
        // classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20")
        // classpath("com.google.dagger:hilt-android-gradle-plugin:2.44")
    }
}
