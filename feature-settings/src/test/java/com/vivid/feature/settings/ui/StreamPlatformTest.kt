package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testet die Plattform-Vorlagen und die Verdrahtung des Hinweistexts des
 * Stream-URL-Felds (SettingsStreamingObsScreen). Die Texte selbst leben in
 * `res/values/strings.xml` und `values-en` (i18n); die Inhalts-Guardianship
 * (RTMP/SRT/Owncast/Presets in beiden Sprachen) übernimmt der CI-Guard
 * `check_i18n.sh`. Dieser Test sichert die Ressourcen-Verdrahtung ab.
 */
class StreamPlatformTest {

    @Test
    fun `Hinweis-Ressource ist verdrahtet`() {
        assertTrue("Hinweis-Ressourcen-ID darf nicht 0 sein", STREAM_URL_HINT_RES != 0)
        assertEquals(R.string.stream_url_hint, STREAM_URL_HINT_RES)
    }

    @Test
    fun `vier Plattform-Optionen in Anzeige-Reihenfolge mit Ingest-URLs`() {
        assertEquals(
            listOf(R.string.platform_twitch, R.string.platform_youtube, R.string.platform_kick, R.string.platform_custom),
            StreamPlatform.entries.map { it.labelRes },
        )
        assertEquals("rtmp://live.twitch.tv/app", StreamPlatform.Twitch.ingestUrl)
        assertEquals("rtmp://a.rtmp.youtube.com/live2", StreamPlatform.YouTube.ingestUrl)
        assertEquals("rtmp://live.kick.com/app", StreamPlatform.Kick.ingestUrl)
        // Custom: leere Ingest-URL, damit beliebige Ziele eingetragen werden können
        assertEquals("", StreamPlatform.Custom.ingestUrl)
    }

    @Test
    fun `jede Plattform hat eine gueltige Label-Ressource`() {
        StreamPlatform.entries.forEach { platform ->
            assertTrue("Label-Ressource von $platform darf nicht 0 sein", platform.labelRes != 0)
        }
    }

    @Test
    fun `custom-Option hat die Benutzerdefiniert-Ressource`() {
        assertEquals(R.string.platform_custom, StreamPlatform.Custom.labelRes)
    }
}
