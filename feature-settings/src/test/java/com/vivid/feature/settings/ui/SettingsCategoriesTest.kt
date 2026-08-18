package com.vivid.feature.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testet die zentrale Kategorie-Definition der neuen Settings-Struktur
 * (Kategorie-Übersicht mit Sub-Screens, wie Moblin). Die Routen müssen mit den
 * `composable(...)`-Definitionen in `MainActivity` übereinstimmen — dieser Test
 * sichert die Struktur ab, damit ein Umbau nie wieder eine Route „verliert“.
 */
class SettingsCategoriesTest {

    @Test
    fun `alle fünf Kategorien vorhanden - in Anzeige-Reihenfolge`() {
        assertEquals(
            listOf("Streaming & OBS", "Overlays & Widgets", "Chat-Bot & KI", "Remote & Datenschutz", "Über & Updates"),
            SettingsCategories.all.map { it.title },
        )
    }

    @Test
    fun `jede Kategorie hat eine eindeutige Route mit korrektem Präfix`() {
        val routes = SettingsCategories.all.map { it.route }
        assertEquals(5, routes.size)
        assertEquals(routes.size, routes.toSet().size) // keine Duplikate
        routes.forEach { route ->
            assertTrue("Route '$route' muss mit 'settings_' beginnen", route.startsWith("settings_"))
        }
    }

    @Test
    fun `jede Kategorie hat Titel Subtitle und Icon`() {
        SettingsCategories.all.forEach { category ->
            assertTrue("Titel fehlt", category.title.isNotBlank())
            assertTrue("Subtitle fehlt für '${category.title}'", category.subtitle.isNotBlank())
            assertNotNull("Icon fehlt für '${category.title}'", category.icon)
        }
    }

    @Test
    fun `Kernrouten enthalten Streaming ChatBot Remote und About`() {
        val routes = SettingsCategories.all.map { it.route }.toSet()
        assertTrue(routes.contains("settings_streaming"))
        assertTrue(routes.contains("settings_overlays"))
        assertTrue(routes.contains("settings_chatbot"))
        assertTrue(routes.contains("settings_remote"))
        assertTrue(routes.contains("settings_about"))
    }
}
