package com.vivid.feature.streaming.ui

import android.Manifest
import android.app.Application
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.vivid.feature.chat.twitch.TwitchChannelUiState
import com.vivid.feature.chat.twitch.TwitchChannelViewModel
import com.vivid.feature.streaming.ColorSpace
import com.vivid.feature.streaming.FocusMode
import com.vivid.feature.streaming.LutPreset
import com.vivid.feature.streaming.ReplayState
import com.vivid.feature.streaming.StreamTargetState
import com.vivid.feature.streaming.StreamTargetStatus
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.StreamingViewModel
import com.vivid.feature.streaming.VideoFilter
import com.vivid.feature.streaming.source.VideoSourceKind
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowActivity

/**
 * Vertrags-Tests zum Permission-Verhalten beim Screen-Eintritt (#249, Vorfall
 * Tag-Run 36849526447): Der CAMERA-Auto-Request aus 94c4e7db feuerte beim
 * bloßen Betreten des Start-Screens den Systemdialog GrantPermissionsActivity
 * ÜBER der MainActivity (im selben Task) — instrumentierte Compose-Tests sahen
 * einen leeren Semantik-Baum ("No compose hierarchies found", 11 Tests /
 * 5 Klassen rot im Tag-Run v0.5.20-beta).
 *
 * Hausregel seit #249: Der Screen-Eintritt fordert NIE Permissions an. Die
 * Kamera-Permission wird nur noch im Go-Live-Flow (explizite User-Geste)
 * angefordert; die Idle-Preview startet nur bei bereits erteilter Permission
 * (die Engine guardt startIdlePreviewIfReady zusätzlich selbst per
 * checkSelfPermission). Der Repair-Pfad auf der Emulator-Gate-Seite
 * (scripts/emulator_test_setup.sh, `adb shell pm grant`-Pendant) bleibt
 * testbar: Diese Tests reparieren den Permission-Zustand VOR dem ersten
 * setContent über grantPermissions/denyPermissions am Shadow-Kontext — der
 * Robolectric-Spiegel der adb-Grants aus der CI-Emulator-Matrix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StreamingScreenPermissionContractTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var engine: StreamingEngine
    private lateinit var viewModel: StreamingViewModel
    private lateinit var twitchViewModel: TwitchChannelViewModel

    private val streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    private val targetStates = MutableStateFlow<List<StreamTargetState>>(emptyList())

    private val appContext = ApplicationProvider.getApplicationContext<Application>()

    private fun grant(vararg permissions: String) {
        shadowOf(appContext as ContextWrapper).grantPermissions(*permissions)
    }

    private fun deny(vararg permissions: String) {
        shadowOf(appContext as ContextWrapper).denyPermissions(*permissions)
    }

    @Before
    fun setUp() {
        engine = mockk(relaxed = true)
        every { engine.streamingState } returns streamingState
        every { engine.targetStates } returns targetStates
        every { engine.focusMode } returns MutableStateFlow(FocusMode.AUTO)
        every { engine.stabilizationEnabled } returns MutableStateFlow(false)
        every { engine.torchEnabled } returns MutableStateFlow(false)
        every { engine.activeSourceKind } returns MutableStateFlow(VideoSourceKind.CAMERA)
        every { engine.activeFilter } returns MutableStateFlow(VideoFilter.NONE)
        every { engine.lowLightBoostEnabled } returns MutableStateFlow(false)
        every { engine.activeLutPreset } returns MutableStateFlow(LutPreset.NONE)
        every { engine.activeColorSpace } returns MutableStateFlow(ColorSpace.SRGB)
        every { engine.replayState } returns MutableStateFlow<ReplayState>(ReplayState.Idle)
        every { engine.exposure } returns MutableStateFlow(0)
        every { engine.exposureRange } returns MutableStateFlow<IntRange?>(null)
        every { engine.autoExposureEnabled } returns MutableStateFlow(true)
        every { engine.autoWhiteBalanceEnabled } returns MutableStateFlow(true)
        every { engine.hasWhiteBalanceControl() } returns false

        viewModel = mockk(relaxed = true)
        every { viewModel.streamingEngine } returns engine
        every { viewModel.configIssues } returns MutableStateFlow(emptyList())
        every { viewModel.scenes } returns MutableStateFlow(emptyList())
        every { viewModel.activeSceneId } returns MutableStateFlow<String?>(null)
        every { viewModel.autoSwitchEnabled } returns MutableStateFlow(false)
        every { viewModel.autoSwitchIntervalSeconds } returns MutableStateFlow(30L)
        every { viewModel.privacyEnabled } returns MutableStateFlow(false)
        every { viewModel.privacyZones } returns MutableStateFlow(emptyList())

        twitchViewModel = mockk(relaxed = true)
        every { twitchViewModel.uiState } returns MutableStateFlow(TwitchChannelUiState())
    }

    /** Setzt den Screen mit leerem Overlay-Slot (keine hiltViewModel-Kinder im Test). */
    private fun setContent() {
        composeRule.setContent {
            StreamingScreen(
                navController = mockk(relaxed = true),
                viewModel = viewModel,
                twitchViewModel = twitchViewModel,
                overlayContent = {},
            )
        }
    }

    /**
     * Der letzte vom Activity-Result-Registry-Pfad angestoßene Permission-Request
     * (Robolectric-Spiegel des Systemdialogs — genau das, was auf dem Emulator
     * als GrantPermissionsActivity über der MainActivity erschien), null = kein
     * Request (Vertrag: Screen-Eintritt darf nie anfragen).
     */
    private fun lastRequestedPermission(): ShadowActivity.PermissionsRequest? =
        shadowOf(composeRule.activity).lastRequestedPermission

    @Test
    fun `entry without camera permission requests nothing and keeps the ui intact`() {
        deny(Manifest.permission.CAMERA)

        setContent()
        composeRule.waitForIdle()

        // #249-Kernvertrag: Der Eintritt startet keinen Systemdialog — der
        // Semantik-Baum bleibt vollständig (das instrumentierte Pendant dieses
        // Vertrags ist der StartupSmokeTest auf dem Emulator ohne Grants).
        composeRule.onNodeWithText("Live Stream").assertIsDisplayed()
        composeRule.onNodeWithText("Start Streaming").assertIsDisplayed()
        org.junit.Assert.assertNull(
            "Screen-Eintritt darf keinen Permission-Request auslösen (#249)",
            lastRequestedPermission(),
        )
        verify(exactly = 0) { viewModel.startStream() }
    }

    @Test
    fun `entry with camera permission granted starts the idle preview`() {
        grant(Manifest.permission.CAMERA)

        setContent()

        composeRule.onNodeWithText("Start Streaming").assertIsDisplayed()
        // Entweder der Entry-Effekt oder der ON_RESUME-Beobachter (beide Pfade
        // sind erlaubt, exakt gezählt wird nur der verbotene Auto-Request).
        verify(atLeast = 1) { engine.startIdlePreviewIfReady() }
    }

    @Test
    fun `go live without camera permission requests permission instead of starting the stream`() {
        // Spiegelbild der Emulator-Matrix-Grants: OHNE die drei Go-Live-Permissions
        // fordert der Go-Live-Pfad exakt die fehlenden drei an (POST_NOTIFICATIONS
        // ab API 33 — die Robolectric-Config pinnt SDK 34).
        deny(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
        )

        setContent()
        composeRule.onNodeWithText("Start Streaming").performClick()
        composeRule.waitForIdle()

        val request = lastRequestedPermission()
        org.junit.Assert.assertNotNull(
            "Go-Live muss die fehlenden Permissions anfordern (User-Geste)",
            request,
        )
        org.junit.Assert.assertEquals(
            setOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
            request!!.requestedPermissions.toSet(),
        )
        // Aber der Stream startet nicht, bevor gegranted wurde.
        verify(exactly = 0) { viewModel.startStream() }
    }

    @Test
    fun `go live with all permissions granted starts the stream without a request`() {
        // Spiegelbild der Emulator-Matrix-Grants (#249): CAMERA + RECORD_AUDIO
        // + POST_NOTIFICATIONS (API-34-Laufwerk) — Go-Live startet direkt.
        grant(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
        )

        setContent()
        composeRule.onNodeWithText("Start Streaming").performClick()
        composeRule.waitForIdle()

        verify(exactly = 1) { viewModel.startStream() }
    }

    @Test
    fun `target status rows render while streaming regardless of permission state`() {
        // Regressionsschutz für den Vorfallsymptom-Kern: auch in der
        // Streaming-Zustandsdarstellung darf kein Dialog-Pfad den Baum leeren.
        grant(Manifest.permission.CAMERA)
        streamingState.value = StreamingState.Streaming
        targetStates.value = listOf(
            StreamTargetState(url = "rtmp://a.example/live", status = StreamTargetStatus.STREAMING),
        )

        setContent()

        composeRule.onNodeWithText("rtmp://a.example/live · live", substring = true).assertIsDisplayed()
    }
}
