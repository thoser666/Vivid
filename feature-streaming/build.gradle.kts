// feature-streaming/build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("dagger.hilt.android.plugin")
    kotlin("kapt")
    id("org.jetbrains.kotlin.plugin.compose") // Add this line
}

android {
    namespace = "com.vivid.feature.streaming" // Updated namespace
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true // Enable Compose for UI elements within the feature module
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.7" // Ensure version matches app module
    }
}

dependencies {
    // Project dependencies
    implementation(project(":domain")) // Feature depends on domain for business logic/models
    implementation(project(":core")) // Feature might use common core utilities

    // Android KTX
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")
    implementation("androidx.activity:activity-compose:1.7.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.44")
    kapt("com.google.dagger:hilt-compiler:2.44")

    // CameraX für Video-Streaming
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("androidx.camera:camera-video:1.4.0") // Für Recording

    // Hilt Navigation Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Permissions (Accompanist)
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Network für RTMP/SRT Streaming
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Für WebSocket (OBS Control)
    implementation("com.squareup.okhttp3:okhttp-ws:4.12.0") // Falls noch verfügbar
    // oder
    implementation("org.java-websocket:Java-WebSocket:1.5.4")

    // Compose (for UI within the feature module)
    implementation("androidx.compose.ui:ui:1.4.3")
    implementation("androidx.compose.material3:material3:1.1.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.4.3") // For @Preview

    // CameraX (core streaming functionality)
    implementation("androidx.camera:camera-core:1.2.3")
    implementation("androidx.camera:camera-camera2:1.2.3")
    implementation("androidx.camera:camera-lifecycle:1.2.3")
    implementation("androidx.camera:camera-view:1.2.3")

// Core Media3 (ExoPlayer Nachfolger)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Protocol Support für IRL-Streaming
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1") // HLS Streams
    implementation("androidx.media3:media3-exoplayer-rtsp:1.4.1") // RTSP Streams
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1") // DASH Streams

    // Für lokale Dateien & verschiedene Formate
    implementation("androidx.media3:media3-decoder:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")

    // Compose Integration
    implementation("androidx.media3:media3-ui-compose:1.4.1") // Neu!

    // Pedro's RTMP für Live-Streaming Output
    implementation(libs.rootencoder)

    // Network & WebSocket
    implementation(libs.okhttp3.okhttp)

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.4.3")
    debugImplementation("androidx.compose.ui:ui-tooling:1.4.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.4.3")
}
