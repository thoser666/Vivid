package com.vivid.irlbroadcaster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressions-Schutz fuer die Per-App-Sprache (Android 13+, UX-Audit-Punkt 5):
 * Das Manifest muss `android:localeConfig` auf `@xml/locales_config`
 * verdrahten, und die Config muss genau die drei unterstuetzten Sprachen
 * (de/en/fr) deklarieren. Dann erscheint Vivid im System-Picker
 * (App-Info > Sprache) und laesst sich pro App von der Systemsprache
 * abweichend umschalten - ohne eigenen In-App-Switch, der nur Redundanz
 * zum System-Setting waere.
 *
 * Hausmuster: [NetworkSecurityConfigTest] (Manifest-Verdrahtung als
 * Datei-Regression, kein Framework noetig).
 */
class PerAppLanguageConfigTest {

    /** Repo-Root finden (Test-JVM laeuft im Modulverzeichnis `app/`). */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "gradlew").exists()) {
            dir = dir.parentFile
        }
        return dir ?: File(".")
    }

    private fun read(relative: String): String =
        File(repoRoot(), relative).readText()

    @Test
    fun `manifest verweist auf die locale config`() {
        val manifest = read("app/src/main/AndroidManifest.xml")
        assertTrue(
            "AndroidManifest.xml muss android:localeConfig=\"@xml/locales_config\" setzen",
            manifest.contains("android:localeConfig=\"@xml/locales_config\""),
        )
    }

    @Test
    fun `locale config deklariert genau die drei unterstuetzten sprachen`() {
        val config = read("app/src/main/res/xml/locales_config.xml")
        val locales =
            Regex("android:name=\"([a-z]+)\"").findAll(config)
                .map { it.groupValues[1] }
                .toList()
        assertEquals(
            "locales_config.xml muss genau de, en und fr deklarieren (Reihenfolge egal, Menge entscheidet)",
            setOf("de", "en", "fr"),
            locales.toSet(),
        )
        assertEquals(
            "locales_config.xml darf keine zusaetzlichen oder doppelten Locales enthalten",
            3,
            locales.size,
        )
    }
}
