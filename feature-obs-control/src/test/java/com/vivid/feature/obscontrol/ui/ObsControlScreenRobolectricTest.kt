package com.vivid.feature.obscontrol.ui

import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vivid.feature.obscontrol.ConnectionState
import com.vivid.feature.obscontrol.ObsControlViewModel
import com.vivid.feature.obscontrol.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Compose-Tests für [ObsControlScreen] — der Screen war bisher der
 * einzige Compose-Block des Moduls ohne UI-Test (0 % LINE, 142 verpasste
 * Zeilen; alle Sibling-Module haben Robolectric-Compose-Tests).
 *
 * Getestet wird der komplette `when (uiState)`-Ast (Disconnected / Connecting /
 * Connected / Error) inklusive der eingebetteten Steuer- und Eingabereihen.
 * Die ViewModel-Logik selbst ist mit ~20 Tests in `ObsControlViewModelTest`
 * abgedeckt — hier zählt nur das Rendering + die Rückverdrahtung der Callbacks.
 *
 * Strings: `values-en` + `qualifiers = "en"`, damit die Assertions
 * sprachdeterministisch sind. Pinnt SDK 34 (Robolectric 4.14.1 bis SDK 35).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ObsControlScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Relaxed-Mock mit allen Flows, die der Screen liest. Die Parameter tragen
     * bewusst andere Namen als die Mock-Properties (sonst gewinnt im
     * `every`-Block die Mock-Property und es kommt zu "Missing mocked calls").
     */
    private fun viewModel(
        connection: ConnectionState = ConnectionState.Disconnected,
        tlsSaved: Boolean = false,
        scene: String? = null,
        inputNames: List<String> = emptyList(),
        mutes: Map<String, Boolean> = emptyMap(),
        levels: Map<String, Float> = emptyMap(),
        offsets: Map<String, Long> = emptyMap(),
        png: ByteArray? = null,
        blackoutOn: Boolean = false,
        blackoutWait: Boolean = false,
    ): ObsControlViewModel = mockk(relaxed = true) {
        every { this@mockk.uiState } returns MutableStateFlow(connection)
        every { this@mockk.savedUseTls } returns MutableStateFlow(tlsSaved)
        every { this@mockk.currentProgramScene } returns MutableStateFlow(scene)
        every { this@mockk.inputs } returns MutableStateFlow(inputNames)
        every { this@mockk.muteStates } returns MutableStateFlow(mutes)
        every { this@mockk.audioLevels } returns MutableStateFlow(levels)
        every { this@mockk.syncOffsets } returns MutableStateFlow(offsets)
        every { this@mockk.snapshot } returns MutableStateFlow(png)
        every { this@mockk.blackoutActive } returns MutableStateFlow(blackoutOn)
        every { this@mockk.blackoutPreparing } returns MutableStateFlow(blackoutWait)
    }

    // --- Disconnected --------------------------------------------------------

    @Test
    fun `disconnected state renders the connection form`() {
        composeRule.setContent { ObsControlScreen(viewModel = viewModel()) }

        composeRule.onNodeWithText("Enter OBS Connection Details").assertIsDisplayed()
        composeRule.onNodeWithText("IP Address").assertIsDisplayed()
        composeRule.onNodeWithText("Port").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("Plain connection (ws://)").assertIsDisplayed()
        composeRule.onNodeWithText("Connect").assertIsDisplayed()
    }

    @Test
    fun `tls switch flips the scheme label and the connect call`() {
        val vm = viewModel()
        composeRule.setContent { ObsControlScreen(viewModel = vm) }

        composeRule.onNodeWithText("Plain connection (ws://)").assertIsDisplayed()
        // Genau ein Toggleable auf diesem Ast: die TLS-Switch-Reihe.
        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNodeWithText("Secure connection (wss://)").assertIsDisplayed()

        composeRule.onNodeWithText("Connect").performClick()
        verify(exactly = 1) { vm.connect("", "192.168.1.100", "4455", true) }
    }

    @Test
    fun `restored tls preference starts on the secure label`() {
        composeRule.setContent { ObsControlScreen(viewModel = viewModel(tlsSaved = true)) }

        composeRule.onNodeWithText("Secure connection (wss://)").assertIsDisplayed()
    }

    // --- Connecting / Error --------------------------------------------------

    @Test
    fun `connecting state shows the progress label`() {
        composeRule.setContent {
            ObsControlScreen(viewModel = viewModel(connection = ConnectionState.Connecting))
        }

        // Achtung: die String-Ressource endet mit Ellipsis „…", nicht mit Punkt.
        composeRule.onNodeWithText("Connecting…").assertIsDisplayed()
        composeRule.onNodeWithText("Enter OBS Connection Details").assertDoesNotExist()
    }

    @Test
    fun `error state renders the resource message and retry disconnects`() {
        val vm = viewModel(connection = ConnectionState.Error(messageRes = R.string.obs_invalid_port_message))
        composeRule.setContent { ObsControlScreen(viewModel = vm) }

        composeRule.onNodeWithText("Error: Invalid port number").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        verify(exactly = 1) { vm.disconnect() }
    }

    @Test
    fun `error state without a resource falls back to the technical message`() {
        composeRule.setContent {
            ObsControlScreen(viewModel = viewModel(connection = ConnectionState.Error(message = "connection refused")))
        }

        composeRule.onNodeWithText("Error: connection refused").assertIsDisplayed()
    }

    // --- Connected -----------------------------------------------------------

    @Test
    fun `connected state renders scene, sections and the input row`() {
        composeRule.setContent {
            ObsControlScreen(
                viewModel = viewModel(
                    connection = ConnectionState.Connected,
                    scene = "Live",
                    inputNames = listOf("Mic/Aux"),
                    mutes = mapOf("Mic/Aux" to true),
                    levels = mapOf("Mic/Aux" to -12.5f),
                    offsets = mapOf("Mic/Aux" to 50_000_000L),
                ),
            )
        }

        composeRule.onNodeWithText("Connected to OBS!").assertIsDisplayed()
        composeRule.onNodeWithText("Current scene: Live").assertIsDisplayed()
        composeRule.onNodeWithText("Screen Black").assertIsDisplayed()
        composeRule.onNodeWithText("Snapshot").assertIsDisplayed()
        composeRule.onNodeWithText("Capture").assertIsDisplayed()
        composeRule.onNodeWithText("Audio Inputs").assertIsDisplayed()
        composeRule.onNodeWithText("Mic/Aux").assertIsDisplayed()
        // 12.5 dB mit einer Nachkommastelle; Sync-Offset 50 ms.
        composeRule.onNodeWithText("-12.5 dB").assertIsDisplayed()
        composeRule.onNodeWithText("50 ms").assertIsDisplayed()
        composeRule.onNodeWithText("Disconnect").assertIsDisplayed()
    }

    @Test
    fun `input row forwards mute toggle and both sync directions`() {
        val vm = viewModel(
            connection = ConnectionState.Connected,
            inputNames = listOf("Mic/Aux"),
            offsets = mapOf("Mic/Aux" to 0L),
        )
        composeRule.setContent { ObsControlScreen(viewModel = vm) }

        // Genau ein Toggleable: die Mute-Switch der Eingabereihe.
        composeRule.onNode(isToggleable()).performClick()
        verify(exactly = 1) { vm.toggleMute("Mic/Aux") }

        val syncButtons = composeRule.onAllNodesWithContentDescription("Shift sync (50 ms)")
        syncButtons[0].performClick()
        verify(exactly = 1) { vm.adjustSyncOffset("Mic/Aux", -1) }
        syncButtons[1].performClick()
        verify(exactly = 1) { vm.adjustSyncOffset("Mic/Aux", 1) }
    }

    @Test
    fun `connected actions call blackout, screenshot and disconnect`() {
        val vm = viewModel(connection = ConnectionState.Connected, scene = "Live")
        composeRule.setContent { ObsControlScreen(viewModel = vm) }

        composeRule.onNodeWithText("Screen Black").performClick()
        verify(exactly = 1) { vm.toggleBlackout() }

        composeRule.onNodeWithText("Capture").performClick()
        verify(exactly = 1) { vm.takeScreenshot() }

        composeRule.onNodeWithText("Disconnect").performClick()
        verify(exactly = 1) { vm.disconnect() }
    }

    @Test
    fun `active blackout relabels the button to restore`() {
        composeRule.setContent {
            ObsControlScreen(viewModel = viewModel(connection = ConnectionState.Connected, blackoutOn = true))
        }

        composeRule.onNodeWithText("Restore scene").assertIsDisplayed()
    }

    @Test
    fun `preparing blackout disables the button and reports progress`() {
        composeRule.setContent {
            ObsControlScreen(viewModel = viewModel(connection = ConnectionState.Connected, blackoutWait = true))
        }

        // Ellipsis „…" statt Punkt — sonst findet `onNodeWithText` den Knoten nicht.
        val label = composeRule.onNodeWithText("Preparing blackout…")
        label.assertIsDisplayed()
        label.assertIsNotEnabled()
    }

    @Test
    fun `snapshot button renders the captured frame`() {
        // 1x1-PNG: BitmapFactory liefert einen Bitmap, der Image-Pfad wird sichtbar.
        val png = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            Base64.DEFAULT,
        )
        composeRule.setContent {
            ObsControlScreen(viewModel = viewModel(connection = ConnectionState.Connected, png = png))
        }

        composeRule.onNodeWithContentDescription("Snapshot").assertIsDisplayed()
    }

    @Test
    fun `inputs section stays hidden without any audio input`() {
        composeRule.setContent {
            ObsControlScreen(viewModel = viewModel(connection = ConnectionState.Connected))
        }

        composeRule.onNodeWithText("Audio Inputs").assertDoesNotExist()
        composeRule.onNodeWithText("Connected to OBS!").assertIsDisplayed()
    }
}
