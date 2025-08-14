// build.gradle.kts (Top-level)
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.0.0") // AGP
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20") // Kotlin
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.44") // Hilt
    }
}

plugins {
    id("com.android.application") version "8.0.0" apply false
    id("com.android.library") version "8.0.0" apply false
    id("org.jetbrains.kotlin.android") version "1.8.20" apply false
    id("dagger.hilt.android.plugin") version "2.44" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}