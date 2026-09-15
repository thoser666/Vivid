package com.vivid.feature.obscontrol

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.repository.StreamingRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObsControlViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun settingsRepository(settings: AppSettings = AppSettings()): SettingsRepository =
        mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(settings)
        }

    @Test
    fun `initial state is Disconnected`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository())

        assertEquals(ConnectionState.Disconnected, viewModel.uiState.value)
    }

    @Test
    fun `connect with valid port delegates to repository and shows Connecting`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.connect("secret", "127.0.0.1", "4455")

        assertEquals(ConnectionState.Connecting, viewModel.uiState.value)
        verify { repository.connectToObs("secret", "127.0.0.1", 4455, false) }
    }

    @Test
    fun `connect forwards the tls flag to the repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.connect("secret", "127.0.0.1", "4455", useTls = true)

        assertEquals(ConnectionState.Connecting, viewModel.uiState.value)
        verify { repository.connectToObs("secret", "127.0.0.1", 4455, true) }
    }

    @Test
    fun `connect with invalid port sets error state and does not connect`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.connect("secret", "127.0.0.1", "not-a-port")

        assertEquals(
            ConnectionState.Error(messageRes = R.string.obs_invalid_port_message),
            viewModel.uiState.value,
        )
        verify(exactly = 0) { repository.connectToObs(any(), any(), any(), any()) }
    }

    @Test
    fun `connect propagates repository exceptions as error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()
        every { repository.connectToObs("secret", "127.0.0.1", 4455, false) } throws RuntimeException("connection refused")

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.connect("secret", "127.0.0.1", "4455")

        assertEquals(ConnectionState.Error("connection refused"), viewModel.uiState.value)
    }

    @Test
    fun `uiState follows the repository connection flow`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val connectedFlow = MutableStateFlow(false)
        val repository = obsRepository(connected = connectedFlow)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        connectedFlow.value = true
        advanceUntilIdle()

        assertEquals(ConnectionState.Connected, viewModel.uiState.value)
    }

    @Test
    fun `disconnect delegates to repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.disconnect()

        verify { repository.disconnectFromObs() }
    }

    @Test
    fun `savedUseTls follows the persisted settings`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository(AppSettings(obsUseTls = true)))
        advanceUntilIdle()

        assertEquals(true, viewModel.savedUseTls.value)
    }

    private fun obsRepository(
        connected: MutableStateFlow<Boolean> = MutableStateFlow(false),
        scenes: MutableStateFlow<List<String>> = MutableStateFlow(emptyList()),
        inputs: MutableStateFlow<List<String>> = MutableStateFlow(emptyList()),
        muteStates: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap()),
        audioLevels: MutableStateFlow<Map<String, Float>> = MutableStateFlow(emptyMap()),
        syncOffsets: MutableStateFlow<Map<String, Long>> = MutableStateFlow(emptyMap()),
        currentProgramScene: MutableStateFlow<String?> = MutableStateFlow(null),
        snapshot: MutableStateFlow<ByteArray?> = MutableStateFlow(null),
    ) = mockk<StreamingRepository>(relaxed = true) {
        every { isConnectedToObs } answers { connected }
        every { obsInputs } answers { inputs }
        every { obsMuteStates } answers { muteStates }
        every { obsAudioLevels } answers { audioLevels }
        every { obsSyncOffsets } answers { syncOffsets }
        every { obsScenes } answers { scenes }
        every { obsCurrentProgramScene } answers { currentProgramScene }
        every { obsSnapshot } answers { snapshot }
    }

    @Test
    fun `connect triggers a data refresh when connection succeeds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val connected = MutableStateFlow(false)
        val repository = obsRepository(connected = connected)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        connected.value = true
        advanceUntilIdle()

        verify { repository.obsRefreshInputs() }
        verify { repository.obsRefreshScenes() }
        verify { repository.obsRefreshProgramScene() }
    }

    @Test
    fun `disconnect does not refresh`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.disconnect()
        advanceUntilIdle()

        verify(exactly = 0) { repository.obsRefreshInputs() }
    }

    @Test
    fun `toggleMute delegates to the repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.toggleMute("Mic/Aux")
        advanceUntilIdle()

        verify { repository.obsToggleMute("Mic/Aux") }
    }

    @Test
    fun `adjustSyncOffset applies delta from the current value`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val syncOffsets = MutableStateFlow(mapOf("Mic/Aux" to 10_000_000L))
        val repository = obsRepository(syncOffsets = syncOffsets)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.adjustSyncOffset("Mic/Aux", 1)
        advanceUntilIdle()

        verify { repository.obsSetSyncOffset("Mic/Aux", 60_000_000L) }
    }

    @Test
    fun `adjustSyncOffset clamps negative delta to zero`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val syncOffsets = MutableStateFlow(mapOf("Mic/Aux" to 10_000_000L))
        val repository = obsRepository(syncOffsets = syncOffsets)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.adjustSyncOffset("Mic/Aux", -1)
        advanceUntilIdle()

        verify { repository.obsSetSyncOffset("Mic/Aux", 0L) }
    }

    @Test
    fun `takeScreenshot sends the current program scene`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val programScene = MutableStateFlow<String?>("Live")
        val repository = obsRepository(currentProgramScene = programScene)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.takeScreenshot()
        advanceUntilIdle()

        verify { repository.obsTakeScreenshot("Live", null, null) }
    }

    @Test
    fun `takeScreenshot does nothing without a program scene`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val programScene = MutableStateFlow<String?>(null)
        val repository = obsRepository(currentProgramScene = programScene)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.takeScreenshot()
        advanceUntilIdle()

        verify(exactly = 0) { repository.obsTakeScreenshot(any(), any(), any()) }
    }

    @Test
    fun `toggleBlackout activates immediately when the scene already exists`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scenes = MutableStateFlow(listOf("Live", ObsControlViewModel.BLACKOUT_SCENE))
        val programScene = MutableStateFlow<String?>("Live")
        val repository = obsRepository(scenes = scenes, currentProgramScene = programScene)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.toggleBlackout()
        advanceUntilIdle()

        verify { repository.obsSetProgramScene(ObsControlViewModel.BLACKOUT_SCENE) }
        assertEquals(true, viewModel.blackoutActive.value)
    }

    @Test
    fun `toggleBlackout creates the scene and waits for the scenes list`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scenes = MutableStateFlow(emptyList<String>())
        val programScene = MutableStateFlow<String?>("Live")
        val repository = obsRepository(scenes = scenes, currentProgramScene = programScene)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.toggleBlackout()
        advanceUntilIdle()

        // Scene creation requested; input + switch not yet.
        verify { repository.obsCreateScene(ObsControlViewModel.BLACKOUT_SCENE) }
        verify(exactly = 0) { repository.obsSetProgramScene(any()) }
        assertEquals(false, viewModel.blackoutActive.value)

        // Scene now appears in the list → collector fires the pending step.
        scenes.value = listOf("Live", ObsControlViewModel.BLACKOUT_SCENE)
        advanceUntilIdle()

        verify { repository.obsCreateBlackoutInput(ObsControlViewModel.BLACKOUT_SCENE, ObsControlViewModel.BLACKOUT_INPUT) }
        verify { repository.obsSetProgramScene(ObsControlViewModel.BLACKOUT_SCENE) }
        assertEquals(true, viewModel.blackoutActive.value)
    }

    @Test
    fun `toggleBlackout restores the previous scene when already active`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scenes = MutableStateFlow(listOf("Live", ObsControlViewModel.BLACKOUT_SCENE))
        val programScene = MutableStateFlow<String?>("Live")
        val repository = obsRepository(scenes = scenes, currentProgramScene = programScene)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.toggleBlackout()
        advanceUntilIdle()
        assertEquals(true, viewModel.blackoutActive.value)

        viewModel.toggleBlackout()
        advanceUntilIdle()

        verify { repository.obsSetProgramScene("Live") }
        assertEquals(false, viewModel.blackoutActive.value)
    }

    @Test
    fun `toggleBlackout ignores a second tap while still preparing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scenes = MutableStateFlow(emptyList<String>())
        val programScene = MutableStateFlow<String?>("Live")
        val repository = obsRepository(scenes = scenes, currentProgramScene = programScene)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.toggleBlackout()
        advanceUntilIdle()

        viewModel.toggleBlackout()
        advanceUntilIdle()

        // CreateScene still only called once.
        verify(exactly = 1) { repository.obsCreateScene(ObsControlViewModel.BLACKOUT_SCENE) }
    }
}
