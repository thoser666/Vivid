package com.vivid.feature.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testet die Plattform-Vorlagen und den Hinweistext des Stream-URL-Felds
 * (SettingsStreamingObsScreen). Der Hinweis ist eine testbare Konstante —
 * er muss die Kernaussagen abdecken (beliebige RTMP(S)/SRT-Ziele, z. B. Owncast),
 * damit die custom-Plattform-Fähigkeit im UI sichtbar und dokumentiert bleibt.
 */
class StreamPlatformTest {

    @Test
    fun `Hinweistext macht beliebige RTMP- und SRT-Ziele sichtbar`() {
        assertTrue("Hinweis muss RTMP erwähnen", STREAM_URL_HINT.contains("RTMP"))
        assertTrue("Hinweis muss SRT erwähnen", STREAM_URL_HINT.contains("SRT"))
    }

    @Test
    fun `Hinweistext nennt Owncast als Beispiel fuer eigene Plattformen`() {
        assertTrue("Hinweis muss Owncast als custom-Beispiel nennen", STREAM_URL_HINT.contains("Owncast"))
    }

    @Test
    fun `Hinweistext stellt klar, dass die Vorlagen nur Presets sind`() {
        assertTrue("Hinweis muss die Vorlagen als Presets einordnen", STREAM_URL_HINT.contains("Presets"))
    }

    @Test
    fun `vier Plattform-Optionen in Anzeige-Reihenfolge mit Ingest-URLs`() {
        assertEquals(
            listOf("Twitch", "YouTube", "Kick", "Benutzerdefiniert"),
            StreamPlatform.entries.map { it.label },
        )
        assertEquals("rtmp://live.twitch.tv/app", StreamPlatform.Twitch.ingestUrl)
        assertEquals("rtmp://a.rtmp.youtube.com/live2", StreamPlatform.YouTube.ingestUrl)
        assertEquals("rtmp://live.kick.com/app", StreamPlatform.Kick.ingestUrl)
        // Custom: leere Ingest-URL, damit beliebige Ziele eingetragen werden können
        assertEquals("", StreamPlatform.Custom.ingestUrl)
    }

    @Test
    fun `custom-Option hat lesbares Label`() {
        assertEquals("Benutzerdefiniert", StreamPlatform.Custom.label)
    }
}
