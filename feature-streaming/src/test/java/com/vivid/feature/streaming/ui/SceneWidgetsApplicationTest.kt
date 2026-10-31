package com.vivid.feature.streaming.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.vivid.core.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** Real repositories and overlay selector, for every widget type in both saved operating modes. */
class SceneWidgetsApplicationTest {
    private class Store : DataStore<Preferences> {
        private val state = MutableStateFlow(preferencesOf())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @ParameterizedTest @EnumSource(WidgetType::class)
    fun `scene and library switches select only the current placement in both modes`(type: WidgetType) = runTest {
        val store = Store()
        val scenes = SceneRepository(store)
        val settings = SettingsRepository(store)
        val overlay = SceneWidgetsOverlayViewModel(scenes)
        val widget = StreamWidget("shared", "Shared", type, configuration = WidgetConfiguration(widgetTemplate = "CONTENT"))
        val placementA = SceneWidget(widget.id, x = 10f, y = 20f, scale = 0.5f)
        val placementB = SceneWidget(widget.id, x = 90f, y = 80f, scale = 1.5f)
        val a = StreamScene("a", "A", widgets = listOf(placementA))
        val b = StreamScene("b", "B", widgets = listOf(placementB))
        scenes.saveWidget(widget)
        scenes.saveScene(a)
        scenes.saveScene(b)
        for (mode in AppOrientation.entries) {
            settings.updateAppOrientation(mode)
            scenes.setActiveScene(a.id)
            assertEquals(listOf(placementA to widget), overlay.state.first().widgets)
            assertEquals(mode, settings.appSettingsFlow.first().appOrientation)
            assertWidgetFlag(settings.widgetSettingsFlow(widget.id).first(), type)

            scenes.saveWidgetWithSceneVisibility(widget.copy(name = "Edited"), mapOf(a.id to false))
            assertTrue(overlay.state.first().widgets.isEmpty())
            assertWidgetFlag(settings.widgetSettingsFlow(widget.id).first(), null)
            assertEquals(placementA.copy(enabled = false), scenes.scenesFlow.first().first().widgets!!.single())

            scenes.saveWidgetWithSceneVisibility(widget, mapOf(a.id to true))
            scenes.setActiveScene(b.id)
            assertEquals(listOf(placementB to widget), overlay.state.first().widgets)
            assertWidgetFlag(settings.widgetSettingsFlow(widget.id).first(), type)

            scenes.saveWidget(widget.copy(enabled = false))
            assertTrue(overlay.state.first().widgets.isEmpty())
            assertWidgetFlag(settings.widgetSettingsFlow(widget.id).first(), null)
            scenes.saveWidget(widget)
            scenes.saveScene(b.copy(enabled = false))
            assertTrue(overlay.state.first().widgets.isEmpty())
            assertWidgetFlag(settings.widgetSettingsFlow(widget.id).first(), null)
            scenes.saveScene(b)
            assertEquals(listOf(placementB to widget), overlay.state.first().widgets)
            scenes.setActiveScene(null)
            assertTrue(overlay.state.first().widgets.isEmpty())
            assertWidgetFlag(settings.widgetSettingsFlow(widget.id).first(), null)
        }
        scenes.deleteWidget(widget.id)
        assertTrue(scenes.scenesFlow.first().all { it.widgets!!.isEmpty() })
    }

    private fun assertWidgetFlag(settings: AppSettings, expected: WidgetType?) {
        val flags = mapOf(WidgetType.TEXT to settings.widgetEnabled, WidgetType.IMAGE to settings.imageWidgetEnabled,
            WidgetType.SLIDESHOW to settings.slideshowWidgetEnabled, WidgetType.QR_CODE to settings.qrCodeWidgetEnabled,
            WidgetType.GRID to settings.gridOverlayEnabled, WidgetType.BATTERY to settings.batteryEnabled,
            WidgetType.CHAT to settings.chatOverlayEnabled, WidgetType.SUBTITLES to settings.subtitlesEnabled,
            WidgetType.POMODORO to settings.pomodoroEnabled)
        for ((type, enabled) in flags) assertEquals(type == expected, enabled, "Unexpected enable flag: $type, expected $expected")
    }
}
