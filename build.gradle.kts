// Top-level build file where you can add configuration options common to all sub-projects/modules.
val compileSdkVersion by extra(36)
val minSdkVersion by extra(26)
val targetSdkVersion by extra(36)

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.dagger.hilt.android") version "2.44" apply false // Corrected Hilt plugin ID
    // id("io.sentry.android.gradle") version "5.9.0" // This was moved from your original plugins block
    // Ensure it's correctly managed if needed.
    // Usually, you would have an alias or the full ID here.
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

/*
// If you are using Kotlin Kapt
kapt {
    correctErrorTypes = true
}
 */

/*
sentry {
    org.set("privat-jb")
    projectName.set("vivid")

    // this will upload your source code to Sentry to show it as part of the stack traces
    // disable if you don't want to expose your sources
    includeSourceContext.set(true)
}
*/
