package com.vivid.feature.streaming.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import com.vivid.core.data.PrivacyZone
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Compose-Smoke-Tests für den [ZoneEditorOverlay] (P1 der Skizze):
 * Editor-Schließen, Anlage bis zur 4-Zonen-Grenze, Löschen der selektierten
 * Zone und ein Touch-Drag auf die Canvas (Zone verschieben). GL/Encoder sind
 * hier bewusst nicht beteiligt — die Ellipsen-Anwendung auf die Pipeline
 * decken die Engine-/Composer-Tests.
 *
 * Pinnt SDK 34 + en-Qualifier, damit String-Assertionen deterministisch sind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZoneEditorRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Setzt den Editor mit einer State-getriebenen Zonen-Liste. */
    private fun setContent(zones: SnapshotStateList<PrivacyZone>, onClose: () -> Unit = {}) {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                ZoneEditorOverlay(
                    zones = zones.toList(),
                    onZonesChange = { updated ->
                        zones.clear()
                        zones.addAll(updated)
                    },
                    onClose = onClose,
                )
            }
        }
    }

    @Test
    fun `editor shows title and hint and reports close`() {
        val zones = mutableStateListOf<PrivacyZone>()
        var closed = false
        setContent(zones) { closed = true }

        composeRule.onNodeWithText("Zone editor (privacy)").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close zone editor").performClick()
        assertTrue(closed)
    }

    @Test
    fun `add zone appends zones up to the cap`() {
        val zones = mutableStateListOf<PrivacyZone>()
        setContent(zones)

        repeat(PrivacyZone.MAX_ZONES) {
            composeRule.onNodeWithContentDescription("Add zone").performClick()
            composeRule.waitForIdle()
        }

        assertEquals(PrivacyZone.MAX_ZONES, zones.size)
        // Kappe erreicht: der Anlage-Button ist deaktiviert.
        composeRule.onNodeWithContentDescription("Add zone").assertIsNotEnabled()
    }

    @Test
    fun `delete removes the selected zone`() {
        val zones = mutableStateListOf<PrivacyZone>()
        setContent(zones)

        composeRule.onNodeWithContentDescription("Add zone").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Delete selected zone").performClick()
        composeRule.waitForIdle()

        assertTrue(zones.isEmpty())
    }

    @Test
    fun `dragging inside a zone moves it`() {
        val zones = mutableStateListOf<PrivacyZone>()
        setContent(zones)

        composeRule.onNodeWithContentDescription("Add zone").performClick()
        composeRule.waitForIdle()
        // Drag von der Bildmitte nach rechts: onDragStart trifft die neue
        // Zone (Zentrum 0.5/0.5). Mehrere Move-Events — das erste wird vom
        // Touch-Slop verzehrt (detectDragGestures), die folgenden landen in
        // onDrag und verschieben das Zentrum.
        composeRule.onNodeWithTag("zone_canvas").performTouchInput {
            down(center)
            repeat(6) { moveBy(androidx.compose.ui.geometry.Offset(40f, 0f)) }
            up()
        }
        composeRule.waitForIdle()

        assertEquals(1, zones.size)
        assertTrue(
            "Zone hätte nach rechts verschoben werden sollen: ${zones[0]}",
            zones[0].centerX > 0.5f,
        )
    }

    @Test
    fun `state flow driven zones render without touching the canvas`() {
        // Zweiter Einstieg (wie die Screen-Integration): Flows als Quelle —
        // korrekt per collectAsState beobachtet (Lint: StateFlowValueCalledInComposition).
        val flow = MutableStateFlow(listOf(PrivacyZone(0.25f, 0.25f, 0.1f, 0.1f)))
        composeRule.setContent {
            val zones by flow.collectAsState()
            Box(Modifier.fillMaxSize()) {
                ZoneEditorOverlay(
                    zones = zones,
                    onZonesChange = {},
                    onClose = {},
                )
            }
        }
        composeRule.onNodeWithTag("zone_editor").assertIsDisplayed()
        composeRule.onNodeWithTag("zone_canvas").assertIsDisplayed()
    }
}
