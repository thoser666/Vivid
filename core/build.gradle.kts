// Stellen Sie sicher, dass am Anfang der Datei "com.android.library" als Plugin steht.
plugins {
    id("com.android.library")
    kotlin("android") // Verwenden Sie kotlin("android") anstelle von kotlin("jvm")
//    id("kotlin-kapt") // Falls Sie Hilt oder andere Annotation Processors hier verwenden
    id("dagger.hilt.android.plugin") // Hilt-Plugin
    id("com.google.devtools.ksp")

    alias(libs.plugins.kotlin.serialization)
}

android {
    // Definieren Sie einen Namespace, dies ist für Android-Bibliotheken erforderlich
    namespace = "com.vivid.core"

    compileSdk = 34 // Verwenden Sie dieselbe SDK-Version wie Ihr App-Modul

    defaultConfig {
        minSdk = 24 // Verwenden Sie dieselbe minSdk wie Ihr App-Modul

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
//    kotlinOptions {
//        jvmTarget = "17"
//    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Fügen Sie Abhängigkeiten hinzu, die in diesem Modul benötigt werden
    implementation(libs.androidx.core.ktx) // Gute Praxis für Android-Bibliotheken
    implementation(libs.androidx.appcompat)

    // Hilt für Dependency Injection
    implementation(libs.hilt.android)
//    kapt(libs.dagger.hilt.compiler)
    ksp(libs.dagger.hilt.compiler)

    // DataStore-Abhängigkeit, die `Context` benötigt
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp3.okhttp) //

    // JSON handling (bereits definiert in Ihrer toml)
    implementation(libs.gson)

    // Optional: Für WebSocket debugging
    implementation(libs.logging.interceptor)

    implementation(libs.kotlinx.serialization.json) // serialization

    implementation(libs.timber) // Für das Logging (behebt 'Unresolved reference timber')
    implementation(libs.obs.websocket.client) // Für die OBS-Verbindung (behebt alle anderen

    // Standard JUnit5 für Unit-Tests
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params) // Für parametrisierte Tests

    // Mockito zum Mocken von Abhängigkeiten (wie z.B. Context)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)

    // Turbine für das Testen von Kotlin Flows
    testImplementation(libs.turbine)

    // Coroutines-Testbibliothek
    testImplementation(libs.kotlinx.coroutines.test)

    // For DataStore-Tests
    testImplementation(libs.androidx.datastore.preferences.core)

    // --- Android-spezifische Test-Abhängigkeiten (bleiben für instrumentierte Tests) ---
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


}
