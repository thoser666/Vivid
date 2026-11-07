package com.vivid.feature.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vivid.core.data.AppSettings
import com.vivid.core.data.LocalOverlaySettings
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsLocalOverlaysTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `interface groups language under theme and opens local overlays from its own section`() {
        var opened = false
        compose.setContent {
            MaterialTheme { SettingsAppearanceScreen(AppSettings(), mockk(relaxed = true),
                onLocalOverlays = { opened = true }, onBack = {}) }
        }
        val mode = compose.onNodeWithTag("orientation_picker").getUnclippedBoundsInRoot()
        val overlaysHeading = compose.onNodeWithTag("appearance_local_overlays_section").getUnclippedBoundsInRoot()
        val entry = compose.onNodeWithTag("appearance_local_overlays").getUnclippedBoundsInRoot()
        val theme = compose.onNodeWithTag("appearance_theme_section").getUnclippedBoundsInRoot()
        val language = compose.onNodeWithTag("language_picker").getUnclippedBoundsInRoot()
        assertTrue(mode.bottom <= overlaysHeading.top && overlaysHeading.bottom <= entry.top)
        assertTrue(entry.bottom <= theme.top && theme.bottom <= language.top)
        compose.onNodeWithText("Configure overlays").assertIsDisplayed()
        compose.onNodeWithTag("appearance_local_overlays").performClick()
        assertTrue(opened)
    }

    @Test @Config(qualifiers = "ru-w400dp-h800dp-xhdpi")
    fun `each whole row toggles only its own setting and renders localized explanation`() {
        var settings by mutableStateOf(LocalOverlaySettings())
        compose.setContent { MaterialTheme { SettingsLocalOverlaysScreen(settings, {}, { settings = it(settings) }) } }
        compose.onNodeWithText("Локальные оверлеи").assertIsDisplayed()
        compose.onNodeWithText("не попадают в трансляцию", substring = true).assertIsDisplayed()
        for (tag in listOf("local_overlay_scenes", "local_overlay_zoom", "local_overlay_fps", "local_overlay_bitrate", "local_overlay_duration")) {
            compose.onNodeWithTag(tag).assertIsOn().performTouchInput { click() }
            compose.onNodeWithTag(tag).assertIsOff()
        }
        org.junit.Assert.assertEquals(LocalOverlaySettings(false, false, false, false, false), settings)
        compose.onNodeWithTag("local_overlay_fps").performClick()
        org.junit.Assert.assertEquals(LocalOverlaySettings(false, true, false, false, false), settings)
        val file = java.io.File("build/reports/local-overlays/settings-ru.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `loading preferences prevents writes through disabled toggles`() {
        var writes = 0
        compose.setContent { MaterialTheme { SettingsLocalOverlaysScreen(null, {}, { writes++ }) } }
        compose.onNodeWithTag("local_overlay_zoom").assertIsNotEnabled().performClick()
        org.junit.Assert.assertEquals(0, writes)
    }
}
