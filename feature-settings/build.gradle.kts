import org.gradle.kotlin.dsl.extra

// Stellen Sie sicher, dass am Anfang der Datei "com.android.library" als Plugin steht.
plugins {
    id("com.android.library")
    kotlin("android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.vivid.feature.settings" // Eindeutiger Namespace für dieses Modul
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // WICHTIG: Aktivieren Sie Compose für dieses Modul
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3" // Stellen Sie sicher, dass diese Version mit Ihrer Kotlin-Version kompatibel ist
    }
}

dependencies {
    // Abhängigkeit zum 'core'-Modul
    implementation(project(":core"))

    // Compose-Abhängigkeiten
    implementation(libs.androidx.compose.bom)
    implementation(libs.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)

    // Hilt für ViewModel-Injection
    implementation(libs.hilt.android)
    kapt(libs.dagger.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Andere notwendige AndroidX-Bibliotheken
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Test-Abhängigkeiten
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.bom)
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}