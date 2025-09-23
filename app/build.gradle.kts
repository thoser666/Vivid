import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    //   id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" // Or the latest compatible version

    id("io.sentry.android.gradle") version "5.11.0"
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vivid.irlbroadcaster"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            // Zuerst versuchen, aus Umgebungsvariablen zu laden (für CI/CD-Systeme wie GitHub Actions)
            val keyStoreFile = System.getenv("SIGNING_KEY_STORE_FILE")
            val keyStorePassword = System.getenv("SIGNING_KEY_STORE_PASSWORD")
            val keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            val keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            if (keyStoreFile != null && File(keyStoreFile).exists()) {
                // CI/CD-Umgebung erkannt
                storeFile = file(keyStoreFile)
                storePassword = keyStorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else {
                // Lokale Entwicklungsumgebung: Lade aus keystore.properties
                val keystorePropertiesFile = rootProject.file("keystore.properties")
                if (keystorePropertiesFile.exists()) {
                    val keystoreProperties = Properties()
                    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                    storeFile = file(keystoreProperties.getProperty("storeFile"))
                    storePassword = keystoreProperties.getProperty("storePassword")
                    this.keyAlias = keystoreProperties.getProperty("keyAlias")
                    this.keyPassword = keystoreProperties.getProperty("keyPassword")
                } else {
                    // Wenn weder CI noch lokale Properties gefunden werden, wird der Build fehlschlagen.
                    // Dies ist besser als eine unsignierte Release-APK zu erstellen.
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.vivid.irlbroadcaster"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = 1 // An integer that increases with each version.
        versionName = "0.0.1" // The string that is displayed to the user.
        // Only package these locales
        androidResources {
            localeFilters.addAll(listOf("en", "fr", "de"))
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        // Eventuell eine Beta-Variante
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
        }

        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Replace the old kotlinOptions block with this
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
//    buildFeatures {
//        compose = true
//    }
//
//    // Add this block if it's not already present and you're not solely relying on the BOM
//    // to set the compiler version, or if the BOM isn't aligning correctly.
//    composeOptions {
//        // Replace "1.5.12" with the actual compatible version you found
//        // from the Compose to Kotlin Compatibility Map for Kotlin 1.9.24.
//        // THIS VERSION MUST BE THE SAME AS IN YOUR :feature-streaming MODULE.
//        kotlinCompilerExtensionVersion = "1.5.14" // Example version, please verify!
//    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.constraintlayout) // Or the latest version

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(project(":feature-streaming")) // Add this line
    implementation(libs.androidx.navigation.compose) // Or the latest version

    implementation(libs.hilt.android)
    implementation(project(":feature-settings"))
    ksp(libs.hilt.compiler)

    // CameraX für Video-Streaming
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video) // Für Recording

    // Hilt Navigation Compose
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.ui)
    implementation(libs.androidx.compose.ui.ui.graphics2)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)

    // Network für RTMP/SRT Streaming
    implementation(libs.okhttp3.okhttp)

    // Für WebSocket (OBS Control)
    implementation(libs.java.websocket)

    // Guava
    implementation(libs.guava) // Or the latest compatible version

    implementation(project(":feature-streaming"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Core Test-Bibliotheken
    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Turbine zum Testen von Kotlin Flows
    testImplementation(libs.turbine)
    // Mockito zum Erstellen von Mock-Objekten (optional, aber nützlich)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)

    // MockK für Mocking in Unit-Tests
    testImplementation(libs.mockk)
    // Coroutines-Test-Helfer
    testImplementation(libs.kotlinx.coroutines.test)

    // (Optional) AndroidX Core für InstantTaskExecutorRule, falls LiveData verwendet wird
    testImplementation(libs.androidx.core.testing)
}

sentry {
    org.set("privat-jb")
    projectName.set("vivid")

    // this will upload your source code to Sentry to show it as part of the stack traces
    // disable if you don't want to expose your sources
    includeSourceContext.set(true)
}
