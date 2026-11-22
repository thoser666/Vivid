plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kover)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    // Sentry plugin only for standard builds (FOSS builds don't use Sentry)
    // The fossBuild property is set via -PfossBuild=true for F-Droid builds
    alias(libs.plugins.sentry.android.gradle)
    // Roborazzi: Screenshot-Testing (visuelle Regression)
    // Voraussetzung: Roborazzi >= 1.65.0 mit Gradle 9 + AGP 9.x
    // separateOutputDirs behebt den Race-Condition-Bug (#830) unter Gradle 9.
    alias(libs.plugins.roborazzi)
}

// Sentry configuration - only active for standard builds
// FOSS builds will have Sentry disabled at runtime
sentry {
    // Skip the ProGuard mapping upload only when no token is available. An env var that
    // is SET BUT EMPTY (`SENTRY_AUTH_TOKEN: ""`, what a missing CI secret produces)
    // must not switch it on. The jobs that build a shipped artifact provide the token;
    // scripts/check_sentry_mapping_upload.sh (M1–M5) enforces that wiring.
    autoUploadProguardMapping.set(
        !System.getenv("SENTRY_AUTH_TOKEN").isNullOrBlank() || localSentryUpload(),
    )
}

/**
 * Local opt-in for the mapping upload: `-PsentryUpload=true` **and** an `auth.token` in
 * the gitignored `sentry.properties` (the same file the plugin reads for org/project).
 * A debug-signed local build must not upload just because the file exists; CI never
 * sets the property — there the env token above is the switch.
 */
private fun localSentryUpload(): Boolean {
    if (project.findProperty("sentryUpload")?.toString()?.toBoolean() != true) return false
    val properties = rootProject.file("sentry.properties").takeIf { it.isFile } ?: return false
    return properties.readLines().any { line ->
        line.substringBefore('=').trim() == "auth.token" &&
            line.substringAfter('=', "").isNotBlank()
    }
}

// Robolectric 4.14.1 bündelt ein älteres ASM, das JDK-25-Klassendateien
// (major version 69) nicht lesen kann — beim Instrumentieren crasht es mit
// "Unsupported class file major version 69". Neuere ASM wird für die
// Unit-Test-Runtime erzwungen (Robolectric nutzt reguläres org.objectweb.asm).
configurations.configureEach {
    if (name.contains("UnitTestRuntimeClasspath")) {
        resolutionStrategy {
            force("org.ow2.asm:asm:9.11.1")
            force("org.ow2.asm:asm-tree:9.11.1")
            force("org.ow2.asm:asm-commons:9.11.1")
            force("org.ow2.asm:asm-util:9.11.1")
            force("org.ow2.asm:asm-analysis:9.11.1")
        }
    }
}

// Roborazzi: separate output dirs per variant (behebt Gradle 9 race condition)
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
roborazzi {
    separateOutputDirs.set(true)
}
// ── Reproduzierbarkeit: Compose-Group-Mapping kanonisieren ─────────────────
// AGP hängt den Inhalt von intermediates/compose_mapping/<variant>/
// compose-mapping.txt an mapping.txt an. Die REIHENFOLGE dieses Blocks ist
// nicht stabil: im Nightly vom 18.11.2026 waren gegenüber einem Rebuild
// desselben Commits 79 von 3.322 Zeilen permutiert — die MENGE war identisch
// (beide Dateien sind als sortierte Menge byte-gleich), der R8-Teil (818.998
// Zeilen) ebenfalls byte-identisch. Weil AGP `# pg_map_hash` über den
// Gesamtinhalt bildet und Sentry `io.sentry.ProguardUuids` aus dem Mapping
// ableitet, kippte damit auch assets/sentry-debug-meta.properties: das APK war
// nicht reproduzierbar, obwohl der obfuskierte Code identisch war.
//
// Deshalb wird der Anhang direkt nach dem Merge byteweise sortiert (der
// Blockkopf `ComposeStackTrace -> $$compose:` bleibt vorn, die Einträge
// darunter in aufsteigender UTF-8-Byte-Reihenfolge = `LC_ALL=C sort`). Ein
// Mapping ist eine Nachschlagetabelle je Zeilenbereich — die Reihenfolge der
// Einträge trägt keine Bedeutung, das Sortieren ist also semantisch neutral,
// macht die Datei aber kanonisch.
//
// ⚠️ Die Sortierung sitzt als `doLast` IN der Merge-Task, nicht in einem
// eigenen Nachlauf-Task: sie muss laufen, BEVOR AGP die Datei an mapping.txt
// anhängt und den Hash bildet. `finalizedBy` allein wäre keine Garantie
// gegenüber den Verbrauchern, und `dependsOn` + `finalizedBy` zusammen ergäbe
// einen Abhängigkeitszyklus. Folge für den Up-to-date-Check: der Merge sieht
// seine eigene, veränderte Ausgabe und läuft beim nächsten Build einmal erneut
// (und sortiert dabei erneut) — danach ist die Ausgabe stabil und die Task
// wieder UP-TO-DATE.

/**
 * Aufsteigende Byte-Reihenfolge der UTF-8-Darstellung — das Äquivalent zu
 * `LC_ALL=C sort`. Bewusst nicht `String.compareTo`: das vergleicht
 * UTF-16-Code-Einheiten und ordnet Zeichen oberhalb U+FFFF anders als ihre
 * Byte-Darstellung. Die Kanonisierung muss auf jeder Maschine (und in jeder
 * Zeichenkodierung des Build-JVM) dieselben Bytes ergeben.
 */
private val composeMappingByteOrder = Comparator<String> { left, right ->
    val leftBytes = left.toByteArray(Charsets.UTF_8)
    val rightBytes = right.toByteArray(Charsets.UTF_8)
    val common = minOf(leftBytes.size, rightBytes.size)
    var index = 0
    while (index < common) {
        val diff = (leftBytes[index].toInt() and 0xFF) - (rightBytes[index].toInt() and 0xFF)
        if (diff != 0) return@Comparator diff
        index++
    }
    leftBytes.size - rightBytes.size
}

/**
 * Kanonische Form eines Compose-Group-Mapping-Files: Blockköpfe (nicht
 * eingerückte Zeilen) und Leerzeilen bleiben an ihrer Position, jeder
 * zusammenhängende Lauf eingerückter Einträge wird sortiert.
 */
private fun canonicalizeComposeMapping(text: String): String {
    val lines = text.split("\n")
    val result = ArrayList<String>(lines.size)
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        if (line.isEmpty() || line.first().isWhitespace()) {
            var end = index
            while (end < lines.size && lines[end].isNotEmpty() && lines[end].first().isWhitespace()) end++
            if (end == index) {
                result.add(line) // Leerzeile: kein Eintrag, Position behalten
                index++
            } else {
                result.addAll(lines.subList(index, end).sortedWith(composeMappingByteOrder))
                index = end
            }
        } else {
            result.add(line) // Blockkopf: Reihenfolge der Blöcke bleibt
            index++
        }
    }
    return result.joinToString("\n")
}

private fun canonicalizeComposeMappingFile(file: File) {
    if (!file.isFile) return
    val original = file.readText()
    val canonical = canonicalizeComposeMapping(original)
    if (canonical == original) return
    file.writeText(canonical)
    println("Compose-Group-Mapping kanonisiert: ${file.path}")
}

// Die Datei wird über den aus dem Task-Namen abgeleiteten Variantenpfad UND
// über die deklarierten Outputs gesucht: kein Raten, falls AGP die Datei in
// einer künftigen Version anders ablegt. Sortieren ist idempotent, doppelte
// Kandidaten sind deshalb unkritisch.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("ComposeMapping") }.configureEach {
    // `configureEach` liefert eine Action MIT Task-Receiver (kein Lambda-Parameter);
    // `name`/`doLast`/`outputs` sind deshalb direkt die der Task.
    val variantName = name.removePrefix("merge").removeSuffix("ComposeMapping")
        .replaceFirstChar { it.lowercase() }
    val derivedMapping = layout.buildDirectory.file(
        "intermediates/compose_mapping/$variantName/compose-mapping.txt",
    )
    doLast {
        val candidates = mutableListOf(derivedMapping.get().asFile)
        outputs.files.files.forEach { path ->
            when {
                path.isFile && path.name == "compose-mapping.txt" -> candidates.add(path)
                path.isDirectory -> path.resolve("compose-mapping.txt").takeIf { f -> f.isFile }
                    ?.let(candidates::add)
            }
        }
        candidates.distinct().forEach { canonicalizeComposeMappingFile(it) }
    }
}

android {
    namespace = "com.vivid"

    lint {
        // Blocking-Check: Lint bricht den Build bei jeder Warnung/Fehler ab.
        // Versionshinweise (GradleDependency/NewerVersionAvailable/AndroidGradlePluginVersion)
        // sind informativ — Dependency-Updates laufen über Dependabot.
        // OldTargetApi: SDK-Upgrade bewusst.
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
            // values-fr/ ist bewusst partiell (nice-to-have, siehe docs/i18n-plan.md §3) —
            // die Pflicht-Sprachen sind values/ (de) + values-en/ (vollständig, CI-geprüft).
            "MissingTranslation",
        )
        warningsAsErrors = true
    }

    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.vivid"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Overridable via -PversionCode= / -PversionName= (CI nightly/stable builds);
        // defaults keep local builds stable.
        val resolvedVersionName = (project.findProperty("versionName") as String?) ?: rootProject.file("VERSION").readText().trim()
        versionName = resolvedVersionName
        fun derivedVersionCode(versionName: String): Int {
            val cleaned = versionName.removePrefix("v")
            val base: String = if (cleaned.contains('-')) cleaned.substringBefore('-') else cleaned
            val parts = base.split('.')
            val major = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
            val minor = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
            val patch = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
            return major * 1_000_000 + minor * 1_000 + patch * 10 + 4
        }
        val resolvedVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: derivedVersionCode(resolvedVersionName)
        versionCode = resolvedVersionCode

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("KEY_ALIAS")
            val keyPassword = System.getenv("KEY_PASSWORD")

            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else {
                // Nur warnen, wenn tatsächlich ein Release-Build (Signing nötig)
                // angefordert wird — Tests/Lint/Debug laufen ohne KEYSTORE_PATH und
                // sind kein Fehler (kein Warn-Rauschen in jedem CI-Schritt).
                val wantsReleaseBuild = gradle.startParameter.taskNames.any {
                    it.contains("Release", ignoreCase = true)
                }
                if (wantsReleaseBuild) {
                    println("⚠️ Release signing not configured - using debug keystore")
                }
            }
        }
        create("upload") {
            // Play-Kanal: signiert das AAB fuer Google Play App Signing, bewusst
            // getrennt vom Release-Key (UPLOAD_*-Secrets, nie KEYSTORE_*).
            val keystorePath = System.getenv("UPLOAD_KEYSTORE_PATH")
            val keystorePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
            val keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")

            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
            // Standard build with Sentry for GitHub/Obtainium/Play Store
            isDefault = true
            buildConfigField("Boolean", "FOSS_BUILD", "false")
        }
        create("foss") {
            dimension = "distribution"
            // FOSS build without Sentry for F-Droid main repository
            // No tracking, no telemetry, fully open-source
            applicationIdSuffix = ".foss"
            buildConfigField("Boolean", "FOSS_BUILD", "true")
        }
    }

    // AppBundleLocaleChanges (Lint): Die App wechselt die Sprache dynamisch
    // (core/i18n/AppLanguage, In-App-Picker unter Darstellung). Ohne diesen
    // Block wuerde der App-Bundle-Precompute die Sprache splitten und ein
    // Basis-APK ausliefern, das die In-App-Auswahl verliert.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            // Sentry environment je Build-Typ (#324): Debug-Builds sind Dev- und
            // CI-Bauten (PR-Validierung läuft auf Debug-APKs). Ohne diesen Tag
            // defaultet Sentry ueberall auf "production" und der environment-Tag
            // unterscheidet Store- von Test-Builds nicht (Beleg in #324).
            // Fehlt ein kuenftiger Build-Type ohne Mapping, bricht die
            // Manifest-Komposition am unersetzten ${SENTRY_ENVIRONMENT} ab.
            manifestPlaceholders["SENTRY_ENVIRONMENT"] = "development"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Store-Bauten (GitHub/Obtainium/Play) — die ehrliche Build-Identitaet
            // hinter dem bisherigen Sentry-Default. playRelease erbt den Wert via
            // initWith unten; Absicht: beide Store-Kanaele tragen dasselbe Environment.
            manifestPlaceholders["SENTRY_ENVIRONMENT"] = "production"

            val releaseConfig = signingConfigs.getByName("release")
            signingConfig = if (releaseConfig.storeFile != null) {
                releaseConfig
            } else {
                signingConfigs.getByName("debug")
            }
        }
        create("playRelease") {
            initWith(getByName("release"))
            // AAB fuer den Play-Kanal: Upload-Key statt Release-Key.
            // Library-Module (core, data, domain, feature-*) kennen nur debug/release:
            // ohne matchingFallbacks schlaegt die Varianten-Aufloesung fehl (kein
            // playRelease-Variant in den Libraries). Fallback auf deren release-Variante.
            matchingFallbacks += listOf("release")
            val uploadConfig = signingConfigs.getByName("upload")
            signingConfig = if (uploadConfig.storeFile != null) {
                uploadConfig
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.kotlin_module"
        }
    }

    testOptions {
        // Robolectric: gemergtes Manifest + Ressourcen in die JVM-Tests laden
        // (nötig für Compose-UI-Tests: stringResource, Theme, Activity-Host)
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Core Modules
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))

    // Feature Modules
    implementation(project(":feature-streaming"))
    implementation(project(":feature-settings"))
    implementation(project(":feature-chat"))
    implementation(project(":feature-widgets"))
    implementation(project(":feature-obs-control"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler.ksp)
    implementation(libs.androidx.hilt.navigation.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.timber)

    // Image Loading (Coil für Inline-Emotes im Chat-Overlay)
    implementation(libs.coil.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    // Roborazzi: Screenshot-Testing (JVM-unit-test, kein Emulator nötig)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    // Compose UI Test für Robolectric (erstellt ComposeRule in unit tests)
    testImplementation(libs.androidx.ui.test.junit4)
    // Robolectric: Android-Framework-Emulation für JVM-basierte UI-Tests
    testImplementation(libs.robolectric.core)
    // Hilt-Test: HiltTestApplication für Robolectric-Tests, die die echte
    // MainActivity starten (echter DI-Graph, aber ohne VividApplication-
    // Nebeneffekte wie Sentry-Init und Ktor-Remote-Control-Server).
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler.ksp)
    androidTestImplementation(libs.rootencoder.library)
    androidTestImplementation(libs.rootencoder.encoder)
    androidTestImplementation(libs.androidx.junit)
    // GrantPermissionRule: die Runtime-Grants der UI-Tests laufen im Testprozess
    // statt per `adb shell pm grant` vor den connected-Tasks (Tag-Run 40849184669).
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    // fastlane screengrab: erzeugt die Play-Store-Screenshots per UI-Test
    // (Lane `capture_play_screenshots`, siehe fastlane/Fastfile)
    androidTestImplementation(libs.screengrab)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
