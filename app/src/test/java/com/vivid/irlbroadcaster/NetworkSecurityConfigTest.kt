package com.vivid.irlbroadcaster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressions-Schutz für die Netzwerk-Sicherheits-Härtung (SonarCloud
 * Security-Hotspot xml:S5332): Cleartext-HTTP muss auf allen
 * Android-Versionen explizit blockiert sein — `android:networkSecurityConfig`
 * im Manifest plus `cleartextTrafficPermitted="false"` in der Base-Config.
 * Ohne den Config-Verweis wäre Cleartext auf API < 28 implizit erlaubt;
 * `usesCleartextTraffic="true"` darf das Manifest hingegen nie setzen.
 */
class NetworkSecurityConfigTest {

    /** Repo-Root finden (Test-JVM läuft im Modulverzeichnis `app/`). */
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
    fun `manifest verweist auf die network security config`() {
        val manifest = read("app/src/main/AndroidManifest.xml")
        assertTrue(
            "AndroidManifest.xml muss android:networkSecurityConfig=\"@xml/network_security_config\" setzen",
            manifest.contains(
                "android:networkSecurityConfig=\"@xml/network_security_config\"",
            ),
        )
    }

    @Test
    fun `base-config blockiert cleartext traffic global`() {
        val config = read("app/src/main/res/xml/network_security_config.xml")
        assertTrue(
            "Base-Config muss cleartextTrafficPermitted=\"false\" setzen",
            config.contains("<base-config cleartextTrafficPermitted=\"false\""),
        )
    }

    @Test
    fun `manifest erlaubt cleartext traffic nirgends per attribut`() {
        val manifest = read("app/src/main/AndroidManifest.xml")
        assertEquals(
            "usesCleartextTraffic=\"true\" darf im Manifest nicht auftreten",
            -1,
            manifest.indexOf("usesCleartextTraffic=\"true\""),
        )
    }
}
