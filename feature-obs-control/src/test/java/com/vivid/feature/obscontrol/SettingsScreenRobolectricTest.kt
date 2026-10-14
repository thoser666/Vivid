package com.vivid.feature.obscontrol

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Compose-Tests für [SettingsScreen] (OBS-Verbindungseinstellungen)
 * — mit [ObsControlScreen] der zweite und letzte Compose-Block des Moduls ohne
 * UI-Test (das Modul stand bei 0 % LINE in `obscontrol/ui`).
 *
 * Der Mock trägt einen echten, zustandsbehafteten UiState: die
 * `on*Changed`-Handler updaten denselben `MutableStateFlow`, den der Screen
 * per `collectAsState` liest — damit sind Szenarien wie "QR-Feld gefüllt →
 * Import-Button wird enabled" ohne Hilt-Container prüfbar. Die
 * ViewModel-Logik selbst deckt `SettingsViewModelTest` ab.
 *
 * Strings: `values-en` + `qualifiers = "en"`. Pinnt SDK 34.
 *
 * Der Screen hat keinen Scroll-Container: die Spalte endet mit einem
 * `Spacer(weight(1f))` und dem Save-Button ganz unten. Robolectric liefert
 * standardmäßig nur ein 320x470dp-Fenster, in das das Formular nicht passt —
 * deshalb hier eine realistische Telefongröße, sonst liegt der Save-Button
 * unter dem Falz und erreicht weder `assertIsDisplayed` noch `performClick`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val state = MutableStateFlow(SettingsUiState())

    private val viewModel: SettingsViewModel = mockk(relaxed = true) {
        every { uiState } returns state
        // Zustandsbehaftet statt relaxed: der Screen liest dieselbe State-Flow,
        // die hier beschrieben wird (sonst bleibt qrInput dauerhaft leer).
        every { onHostChanged(any()) } answers { state.update { it.copy(host = firstArg()) } }
        every { onPortChanged(any()) } answers { state.update { it.copy(port = firstArg()) } }
        every { onPasswordChanged(any()) } answers { state.update { it.copy(password = firstArg()) } }
        every { onUseTlsChanged(any()) } answers { state.update { it.copy(useTls = firstArg()) } }
        every { onQrInputChanged(any()) } answers { state.update { it.copy(qrInput = firstArg()) } }
        every { importFromQrCode(any()) } returns QrImportResult.Error("invalid")
    }

    // --- Rendering -----------------------------------------------------------

    @Test
    fun `renders every field label and action`() {
        composeRule.setContent { SettingsScreen(viewModel = viewModel) }

        composeRule.onNodeWithText("OBS Host").assertIsDisplayed()
        composeRule.onNodeWithText("OBS Port").assertIsDisplayed()
        composeRule.onNodeWithText("OBS Password").assertIsDisplayed()
        composeRule.onNodeWithText("Secure connection (wss://)").assertIsDisplayed()
        composeRule.onNodeWithText("Import from QR code (paste content)").assertIsDisplayed()
        composeRule.onNodeWithText("Import").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun `renders the stored connection values`() {
        state.value = SettingsUiState(host = "studio.local", port = "7777")
        composeRule.setContent { SettingsScreen(viewModel = viewModel) }

        composeRule.onNodeWithText("studio.local").assertIsDisplayed()
        composeRule.onNodeWithText("7777").assertIsDisplayed()
    }

    // --- Eingaben ------------------------------------------------------------

    @Test
    fun `host, port and password edits are forwarded to the viewmodel`() {
        composeRule.setContent { SettingsScreen(viewModel = viewModel) }

        // Vier TextField-Knoten in Kompositionsreihenfolge: Host, Port,
        // Passwort, QR. Der Eingabezeichen-Text hängt von der Cursorposition
        // ab — es zählt, dass das Zeichen den passenden Handler erreicht.
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("h")
        verify(exactly = 1) { viewModel.onHostChanged(match { it.contains("h") }) }
        fields[1].performTextInput("9")
        verify(exactly = 1) { viewModel.onPortChanged(match { it.contains("9") }) }
        fields[2].performTextInput("s")
        verify(exactly = 1) { viewModel.onPasswordChanged(match { it.contains("s") }) }
    }

    @Test
    fun `tls switch forwards the toggle`() {
        composeRule.setContent { SettingsScreen(viewModel = viewModel) }

        // Genau ein Toggleable auf dem Screen: die TLS-Switch-Reihe.
        composeRule.onNode(isToggleable()).performClick()
        verify(exactly = 1) { viewModel.onUseTlsChanged(true) }
    }

    // --- QR-Import -----------------------------------------------------------

    @Test
    fun `qr import stays disabled until content is pasted and then forwards it`() {
        composeRule.setContent { SettingsScreen(viewModel = viewModel) }

        composeRule.onNodeWithText("Import").assertIsNotEnabled()

        val qr = composeRule.onAllNodes(hasSetTextAction())[3]
        qr.performTextInput("obws://studio.local:4455/secret")

        composeRule.onNodeWithText("Import").assertIsEnabled()
        composeRule.onNodeWithText("Import").performClick()
        verify(exactly = 1) { viewModel.importFromQrCode("obws://studio.local:4455/secret") }
    }

    @Test
    fun `qr error message is rendered below the field`() {
        state.value = SettingsUiState(qrMessage = "Invalid QR code: nope")
        composeRule.setContent { SettingsScreen(viewModel = viewModel) }

        composeRule.onNodeWithText("Invalid QR code: nope").assertIsDisplayed()
    }

    // --- Speichern -----------------------------------------------------------

    @Test
    fun `save button forwards the save action`() {
        composeRule.setContent { SettingsScreen(viewModel = viewModel) }

        composeRule.onNodeWithText("Save").performClick()
        verify(exactly = 1) { viewModel.saveObsSettings() }
    }

    @Test
    fun `saving replaces the save label with the progress indicator`() {
        state.value = SettingsUiState(isSaving = true)
        composeRule.setContent { SettingsScreen(viewModel = viewModel) }

        // Der Button bleibt existent, trägt aber nur noch den Spinner.
        composeRule.onNodeWithText("Save").assertDoesNotExist()
    }
}
