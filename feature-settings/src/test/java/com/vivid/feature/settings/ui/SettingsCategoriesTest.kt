package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
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
    fun `alle acht Kategorien vorhanden - in Anzeige-Reihenfolge`() {
        assertEquals(
            listOf(
                R.string.cat_streaming_title,
                R.string.camera_section_title,
                R.string.cat_appearance_title,
                R.string.cat_overlays_title,
                R.string.cat_chatbot_title,
                R.string.cat_remote_title,
                R.string.cat_about_title,
                R.string.cat_logs_title,
            ),
            SettingsCategories.all.map { it.titleRes },
        )
    }

    @Test
    fun `jede Kategorie hat eine eindeutige Route mit korrektem Präfix`() {
        val routes = SettingsCategories.all.map { it.route }
        assertEquals(8, routes.size)
        assertEquals(routes.size, routes.toSet().size) // keine Duplikate
        routes.forEach { route ->
            assertTrue("Route '$route' muss mit 'settings_' beginnen", route.startsWith("settings_"))
        }
    }

    @Test
    fun `jede Kategorie hat Titel- Subtitle-Ressource und Icon`() {
        SettingsCategories.all.forEach { category ->
            assertTrue("Titel-Ressource fehlt", category.titleRes != 0)
            assertTrue("Subtitle-Ressource fehlt", category.subtitleRes != 0)
            assertNotNull("Icon fehlt", category.icon)
        }
    }

    @Test
    fun `Kernrouten enthalten Streaming Camera Appearance ChatBot Remote und About`() {
        val routes = SettingsCategories.all.map { it.route }.toSet()
        assertTrue(routes.contains("settings_streaming"))
        assertTrue(routes.contains("settings_camera"))
        assertTrue(routes.contains("settings_appearance"))
        assertTrue(routes.contains("settings_overlays"))
        assertTrue(routes.contains("settings_chatbot"))
        assertTrue(routes.contains("settings_remote"))
        assertTrue(routes.contains("settings_about"))
        assertTrue(routes.contains("settings_logs"))
    }
}
