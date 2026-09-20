package com.vivid.feature.obscontrol

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.vivid.core.data.SettingsRepository
import com.vivid.core.repository.StreamingRepository
import com.vivid.domain.model.LoginRequest
import com.vivid.domain.model.LoginResult
import com.vivid.domain.model.RegistrationRequest
import com.vivid.domain.model.RegistrationResult
import com.vivid.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

/**
 * Bewusst mockk-frei: Der MockK-Agent attached den Byte-Buddy-Agent dynamisch
 * (JEP 451); unter CI-Last racet der Attach mit der Instrumentierung —
 * sichtbar als MockKException -> ClassCastException an der ersten every-Zeile
 * (2x CI-Vorfall am 20.09.2026). StreamingRepository läuft hier als
 * handgeschriebener Fake mit Call-Log, SettingsRepository als echte Instanz
 * über einen Fake-DataStore (appSettingsFlow durchläuft die echte
 * Combine-Pipeline). Der Test ist damit deterministisch und agent-frei.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObsControlViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Fakes ────────────────────────────────────────────────────────────

    /** Minimaler DataStore-Fake: state-backed data-Flow, in-memory update. */
    private class FakeDataStore(
        initial: Preferences = preferencesOf(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences {
            state.value = transform(state.value)
            return state.value
        }
    }

    /**
     * Handgeschriebener StreamingRepository-Fake: überschreibbare Flows plus
     * Call-Log ("methode(arg1,arg2)") in Aufrufreihenfolge. Nicht genutzte
     * Members brechen laut (fail-loud) statt still Verhalten zu erfinden.
     */
    private class FakeStreamingRepository(
        override val isConnectedToObs: MutableStateFlow<Boolean> = MutableStateFlow(false),
        override val obsScenes: MutableStateFlow<List<String>> = MutableStateFlow(emptyList()),
        override val obsInputs: MutableStateFlow<List<String>> = MutableStateFlow(emptyList()),
        override val obsMuteStates: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap()),
        override val obsAudioLevels: MutableStateFlow<Map<String, Float>> = MutableStateFlow(emptyMap()),
        override val obsSyncOffsets: MutableStateFlow<Map<String, Long>> = MutableStateFlow(emptyMap()),
        override val obsCurrentProgramScene: MutableStateFlow<String?> = MutableStateFlow(null),
        override val obsSnapshot: MutableStateFlow<ByteArray?> = MutableStateFlow(null),
    ) : StreamingRepository {

        /** Wenn gesetzt, wirft der nächste connectToObs-Aufruf diesen Fehler. */
        var connectError: RuntimeException? = null

        val calls = mutableListOf<String>()

        private fun record(name: String, vararg args: Any?) {
            calls += name + "(" + args.joinToString(",") { it.toString() } + ")"
        }

        fun count(name: String): Int = calls.count { it.substringBefore('(') == name }

        fun last(name: String): String =
            calls.last { it.substringBefore('(') == name }

        override fun connectToObs(password: String, ip: String, port: Int, useTls: Boolean) {
            connectError?.let { throw it }
            record("connectToObs", password, ip, port, useTls)
        }

        override fun disconnectFromObs() = record("disconnectFromObs")

        override fun obsRefreshInputs() = record("obsRefreshInputs")

        override fun obsRefreshScenes() = record("obsRefreshScenes")

        override fun obsRefreshProgramScene() = record("obsRefreshProgramScene")

        override fun obsToggleMute(inputName: String) = record("obsToggleMute", inputName)

        override fun obsRefreshSyncOffset(inputName: String) = record("obsRefreshSyncOffset", inputName)

        override fun obsSetSyncOffset(inputName: String, syncOffsetNs: Long) =
            record("obsSetSyncOffset", inputName, syncOffsetNs)

        override fun obsSetProgramScene(sceneName: String) = record("obsSetProgramScene", sceneName)

        override fun obsCreateScene(sceneName: String) = record("obsCreateScene", sceneName)

        override fun obsCreateBlackoutInput(sceneName: String, inputName: String) =
            record("obsCreateBlackoutInput", sceneName, inputName)

        override fun obsTakeScreenshot(sourceName: String, width: Int?, height: Int?) =
            record("obsTakeScreenshot", sourceName, width, height)

        override fun getObsScenes(): List<String> = obsScenes.value

        override suspend fun login(loginRequest: LoginRequest): LoginResult =
            error("in diesen Tests ungenutzt")

        override suspend fun register(registrationRequest: RegistrationRequest): RegistrationResult =
            error("in diesen Tests ungenutzt")

        override suspend fun getAccount(userId: Int): User = error("in diesen Tests ungenutzt")

        override suspend fun updateAccount(userId: Int, user: User): User =
            error("in diesen Tests ungenutzt")

        override suspend fun deleteAccount(userId: Int) = error("in diesen Tests ungenutzt")

        override suspend fun getFollowers(userId: Int): List<User> =
            error("in diesen Tests ungenutzt")

        override suspend fun getFollowing(userId: Int): List<User> =
            error("in diesen Tests ungenutzt")

        override suspend fun followUser(userId: Int, followId: Int) =
            error("in diesen Tests ungenutzt")

        override suspend fun unfollowUser(userId: Int, unfollowId: Int) =
            error("in diesen Tests ungenutzt")

        override suspend fun getStreamKey(userId: Int): String =
            error("in diesen Tests ungenutzt")
    }

    /**
     * Echte SettingsRepository-Instanz auf Fake-DataStore — appSettingsFlow
     * durchläuft die echte Combine-Pipeline (näher am Produkt als ein Mock).
     */
    private fun settingsRepository(obsUseTls: Boolean = false): SettingsRepository =
        SettingsRepository(
            FakeDataStore(
                if (obsUseTls) {
                    preferencesOf(booleanPreferencesKey("obs_use_tls") to true)
                } else {
                    preferencesOf()
                },
            ),
        )

    private fun obsRepository(
        connected: MutableStateFlow<Boolean> = MutableStateFlow(false),
        scenes: MutableStateFlow<List<String>> = MutableStateFlow(emptyList()),
        inputs: MutableStateFlow<List<String>> = MutableStateFlow(emptyList()),
        muteStates: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap()),
        audioLevels: MutableStateFlow<Map<String, Float>> = MutableStateFlow(emptyMap()),
        syncOffsets: MutableStateFlow<Map<String, Long>> = MutableStateFlow(emptyMap()),
        currentProgramScene: MutableStateFlow<String?> = MutableStateFlow(null),
        snapshot: MutableStateFlow<ByteArray?> = MutableStateFlow(null),
    ) = FakeStreamingRepository(
        isConnectedToObs = connected,
        obsScenes = scenes,
        obsInputs = inputs,
        obsMuteStates = muteStates,
        obsAudioLevels = audioLevels,
        obsSyncOffsets = syncOffsets,
        obsCurrentProgramScene = currentProgramScene,
        obsSnapshot = snapshot,
    )

    // ── Tests ────────────────────────────────────────────────────────────

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
        assertEquals(
            "connectToObs(secret,127.0.0.1,4455,false)",
            repository.last("connectToObs"),
        )
    }

    @Test
    fun `connect forwards the tls flag to the repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.connect("secret", "127.0.0.1", "4455", useTls = true)

        assertEquals(ConnectionState.Connecting, viewModel.uiState.value)
        assertEquals(
            "connectToObs(secret,127.0.0.1,4455,true)",
            repository.last("connectToObs"),
        )
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
        assertEquals(0, repository.count("connectToObs"))
    }

    @Test
    fun `connect propagates repository exceptions as error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()
        repository.connectError = RuntimeException("connection refused")

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

        assertEquals(1, repository.count("disconnectFromObs"))
    }

    @Test
    fun `savedUseTls follows the persisted settings`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository(obsUseTls = true))
        advanceUntilIdle()

        assertEquals(true, viewModel.savedUseTls.value)
    }

    @Test
    fun `connect triggers a data refresh when connection succeeds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val connected = MutableStateFlow(false)
        val repository = obsRepository(connected = connected)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        connected.value = true
        advanceUntilIdle()

        assertEquals(1, repository.count("obsRefreshInputs"))
        assertEquals(1, repository.count("obsRefreshScenes"))
        assertEquals(1, repository.count("obsRefreshProgramScene"))
    }

    @Test
    fun `disconnect does not refresh`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.disconnect()
        advanceUntilIdle()

        assertEquals(0, repository.count("obsRefreshInputs"))
    }

    @Test
    fun `toggleMute delegates to the repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = obsRepository()

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.toggleMute("Mic/Aux")
        advanceUntilIdle()

        assertEquals("obsToggleMute(Mic/Aux)", repository.last("obsToggleMute"))
    }

    @Test
    fun `adjustSyncOffset applies delta from the current value`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val syncOffsets = MutableStateFlow(mapOf("Mic/Aux" to 10_000_000L))
        val repository = obsRepository(syncOffsets = syncOffsets)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.adjustSyncOffset("Mic/Aux", 1)
        advanceUntilIdle()

        assertEquals(
            "obsSetSyncOffset(Mic/Aux,60000000)",
            repository.last("obsSetSyncOffset"),
        )
    }

    @Test
    fun `adjustSyncOffset clamps negative delta to zero`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val syncOffsets = MutableStateFlow(mapOf("Mic/Aux" to 10_000_000L))
        val repository = obsRepository(syncOffsets = syncOffsets)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.adjustSyncOffset("Mic/Aux", -1)
        advanceUntilIdle()

        assertEquals("obsSetSyncOffset(Mic/Aux,0)", repository.last("obsSetSyncOffset"))
    }

    @Test
    fun `takeScreenshot sends the current program scene`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val programScene = MutableStateFlow<String?>("Live")
        val repository = obsRepository(currentProgramScene = programScene)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.takeScreenshot()
        advanceUntilIdle()

        assertEquals("obsTakeScreenshot(Live,null,null)", repository.last("obsTakeScreenshot"))
    }

    @Test
    fun `takeScreenshot does nothing without a program scene`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val programScene = MutableStateFlow<String?>(null)
        val repository = obsRepository(currentProgramScene = programScene)

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.takeScreenshot()
        advanceUntilIdle()

        assertEquals(0, repository.count("obsTakeScreenshot"))
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

        assertEquals(
            "obsSetProgramScene(${ObsControlViewModel.BLACKOUT_SCENE})",
            repository.last("obsSetProgramScene"),
        )
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
        assertEquals(
            "obsCreateScene(${ObsControlViewModel.BLACKOUT_SCENE})",
            repository.last("obsCreateScene"),
        )
        assertEquals(0, repository.count("obsSetProgramScene"))
        assertEquals(false, viewModel.blackoutActive.value)

        // Scene now appears in the list → collector fires the pending step.
        scenes.value = listOf("Live", ObsControlViewModel.BLACKOUT_SCENE)
        advanceUntilIdle()

        assertEquals(
            "obsCreateBlackoutInput(${ObsControlViewModel.BLACKOUT_SCENE},${ObsControlViewModel.BLACKOUT_INPUT})",
            repository.last("obsCreateBlackoutInput"),
        )
        assertEquals(
            "obsSetProgramScene(${ObsControlViewModel.BLACKOUT_SCENE})",
            repository.last("obsSetProgramScene"),
        )
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

        assertEquals("obsSetProgramScene(Live)", repository.last("obsSetProgramScene"))
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
        assertEquals(1, repository.count("obsCreateScene"))
    }
}
