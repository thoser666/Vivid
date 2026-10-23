package com.vivid.feature.settings.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.vivid.core.data.AccentColor
import com.vivid.core.data.AppSettings
import com.vivid.core.data.ThemeMode
import com.vivid.feature.settings.R
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceLayoutRobolectricTest {
    @get:Rule val compose = createComposeRule()

    private fun checkLayout(fontScale: Float, screenshotName: String) {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SettingsAppearanceScreen(AppSettings(), vm) {}
            }
        }

        fun checkText(label: String, node: SemanticsNodeInteraction = compose.onNodeWithText(label)) {
            node.performScrollTo().assertIsDisplayed()
            val viewport = compose.onNodeWithTag("settings_section_content").getUnclippedBoundsInRoot()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$label extends beyond the left edge", bounds.left >= viewport.left)
            assertTrue("$label extends beyond the right edge", bounds.right <= viewport.right)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("$label must have a text layout", layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertFalse("$label loses lines", layout.multiParagraph.didExceedMaxLines)
                for (line in 0 until layout.lineCount) {
                    assertFalse("$label is ellipsized", layout.isLineEllipsized(line))
                    assertTrue("$label extends past its text box", layout.getLineLeft(line) >= -1f &&
                        layout.getLineRight(line) <= layout.size.width + 1f)
                    assertTrue("$label is clipped vertically", layout.getLineBottom(line) <= layout.size.height + 1f)
                }
                org.junit.Assert.assertEquals("$label loses characters", layout.layoutInput.text.length,
                    layout.getLineEnd(layout.lineCount - 1))
            }
        }

        ThemeMode.entries.forEach { mode ->
            val label = context.getString(mode.displayNameRes)
            val option = compose.onNode(hasText(label).and(isSelectable()))
            checkText(label, option)
            option.performClick()
            verify { vm.onThemeModeChange(mode) }
        }
        screenshot("$screenshotName-themes")
        AccentColor.entries.forEach { accent ->
            val label = context.getString(accent.displayNameRes)
            compose.onNodeWithTag("accent_picker").performScrollTo().performClick()
            val option = compose.onNodeWithTag("accent_option_${accent.name}")
            checkText(label, option)
            compose.onNode(hasTestTag("accent_sample_${accent.name}").and(hasAnyAncestor(hasTestTag("accent_option_${accent.name}"))), useUnmergedTree = true).assertExists()
            if (accent == AccentColor.entries.last()) screenshot("$screenshotName-dropdown")
            option.performClick()
            verify { vm.onAccentColorChange(accent) }
        }
        compose.onNodeWithText(context.getString(R.string.settings_save_button)).performScrollTo().assertIsDisplayed()
        screenshot(screenshotName)
    }

    private fun screenshot(name: String) {
        val file = java.io.File("build/reports/appearance/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test @Config(qualifiers = "en-w320dp-h480dp")
    fun `english fits a 320dp phone`() = checkLayout(1f, "en-320")

    @Test @Config(qualifiers = "ru-w280dp-h480dp")
    fun `russian fits a 280dp phone with enlarged text`() = checkLayout(1.3f, "ru-280-large")

    @Test @Config(qualifiers = "fr-w320dp-h480dp")
    fun `french fits a 320dp phone with enlarged text`() = checkLayout(1.5f, "fr-320-large")

    @Test @Config(qualifiers = "de-w360dp-h480dp")
    fun `german fits at double font scale`() = checkLayout(2f, "de-360-double")

    @Test @Config(qualifiers = "en-w480dp-h280dp-land")
    fun `short landscape can scroll to every option and save`() = checkLayout(1.3f, "en-landscape")
}
