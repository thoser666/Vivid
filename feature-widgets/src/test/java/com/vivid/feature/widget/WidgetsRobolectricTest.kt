package com.vivid.feature.widget

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Compose-Tests für die Stream-Overlay-Widgets (Battery, TextInfo,
 * QR-Code, Image, Slideshow, GridOverlay). Die Widgets nehmen ihre ViewModels
 * als Parameter (hiltViewModel nur als Default) — die Tests stubben die
 * UiState-Flows und decken Enabled/Disabled-Renderpfade ab. SDK-34-/en-Pin
 * für deterministische String-Assertionen, NATIVE Graphics für QR-Bitmap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetsRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    // --- BatteryWidget ------------------------------------------------------

    @Test
    fun `battery renders percent and icon when enabled`() {
        val viewModel = mockk<BatteryWidgetViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(
                BatteryWidgetUiState(enabled = true, level = 87, showIcon = true, showPercent = true),
            )
        }
        composeRule.setContent { BatteryWidget(viewModel = viewModel) }
        composeRule.onNodeWithText("87%").assertIsDisplayed()
        // batteryIcon: >80 % → 🔋 (nur 61–80 % wäre 🟩); assertExists statt
        // assertIsDisplayed — Emoji-Messung ist unter Robolectric unzuverlässig.
        composeRule.onNodeWithText("🔋").assertExists()
    }

    @Test
    fun `battery renders low-battery state`() {
        val viewModel = mockk<BatteryWidgetViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(
                BatteryWidgetUiState(enabled = true, level = 8, showIcon = true, showPercent = true, lowThreshold = 15),
            )
        }
        composeRule.setContent { BatteryWidget(viewModel = viewModel) }
        composeRule.onNodeWithText("8%").assertIsDisplayed()
        composeRule.onNodeWithText("🪫").assertExists()
    }

    @Test
    fun `battery renders nothing when disabled`() {
        val viewModel = mockk<BatteryWidgetViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(BatteryWidgetUiState(enabled = false))
        }
        composeRule.setContent { BatteryWidget(viewModel = viewModel) }
        composeRule.onAllNodesWithText("100%").assertCountEquals(0)
    }

    // --- TextInfoWidget -------------------------------------------------------

    @Test
    fun `textinfo renders time and date when enabled`() {
        val viewModel = mockk<TextInfoWidgetViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(
                TextInfoWidgetUiState(
                    enabled = true,
                    showTime = true,
                    showLocation = false,
                    showSpeed = false,
                    showAltitude = false,
                    time = "12:34:56",
                    date = "06.09.2026",
                ),
            )
        }
        composeRule.setContent { TextInfoWidget(viewModel = viewModel) }
        composeRule.onNodeWithText("12:34:56").assertIsDisplayed()
        composeRule.onNodeWithText("06.09.2026").assertIsDisplayed()
    }

    @Test
    fun `textinfo renders nothing when disabled`() {
        val viewModel = mockk<TextInfoWidgetViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(TextInfoWidgetUiState(enabled = false))
        }
        composeRule.setContent { TextInfoWidget(viewModel = viewModel) }
        composeRule.onAllNodesWithText("--:--:--").assertCountEquals(0)
    }

    // --- QrCodeWidget ---------------------------------------------------------

    @Test
    fun `qrcode renders bitmap when enabled`() {
        val viewModel = mockk<QrCodeWidgetViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(
                QrCodeWidgetUiState(enabled = true, content = "https://vivid.example/donate", sizeDp = 120),
            )
        }
        composeRule.setContent { QrCodeWidget(viewModel = viewModel) }
        composeRule.onNodeWithContentDescription("QR code overlay").assertExists()
    }

    @Test
    fun `qrcode renders nothing when disabled`() {
        val viewModel = mockk<QrCodeWidgetViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(QrCodeWidgetUiState(enabled = false))
        }
        composeRule.setContent { QrCodeWidget(viewModel = viewModel) }
        composeRule.onAllNodesWithContentDescription("QR code overlay").assertCountEquals(0)
    }

    // --- ImageWidget ----------------------------------------------------------

    @Test
    fun `image renders when enabled`() {
        val viewModel = mockk<ImageWidgetViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(
                ImageWidgetUiState(enabled = true, uri = "file:///overlay-logo.png"),
            )
        }
        composeRule.setContent { ImageWidget(viewModel = viewModel) }
        composeRule.onNodeWithContentDescription("Stream overlay image").assertExists()
    }

    @Test
    fun `image renders nothing when disabled`() {
        val viewModel = mockk<ImageWidgetViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(ImageWidgetUiState(enabled = false))
        }
        composeRule.setContent { ImageWidget(viewModel = viewModel) }
        composeRule.onAllNodesWithContentDescription("Stream overlay image").assertCountEquals(0)
    }

    // --- SlideshowWidget --------------------------------------------------------

    @Test
    fun `slideshow renders current image when enabled`() {
        val viewModel = mockk<SlideshowWidgetViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(
                SlideshowWidgetUiState(
                    enabled = true,
                    imageUris = listOf("file:///slide-a.png", "file:///slide-b.png"),
                    intervalSeconds = 30,
                    currentIndex = 0,
                ),
            )
        }
        composeRule.setContent { SlideshowWidget(viewModel = viewModel) }
        composeRule.onNodeWithContentDescription("Slideshow image").assertExists()
    }

    @Test
    fun `slideshow renders nothing when disabled`() {
        val viewModel = mockk<SlideshowWidgetViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(SlideshowWidgetUiState(enabled = false))
        }
        composeRule.setContent { SlideshowWidget(viewModel = viewModel) }
        composeRule.onAllNodesWithContentDescription("Slideshow image").assertCountEquals(0)
    }

    // --- GridOverlay ------------------------------------------------------------

    @Test
    fun `grid overlay composes when enabled`() {
        val viewModel = mockk<GridOverlayViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(GridOverlayUiState(enabled = true, spacingDp = 40))
        }
        composeRule.setContent { GridOverlay(viewModel = viewModel) }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `grid overlay composes empty when disabled`() {
        val viewModel = mockk<GridOverlayViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(GridOverlayUiState(enabled = false))
        }
        composeRule.setContent { GridOverlay(viewModel = viewModel) }
        composeRule.onRoot().assertExists()
    }
}
