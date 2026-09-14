package com.vivid.core.network.obs

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vivid.core.network.obs.requests.CreateInput
import com.vivid.core.network.obs.requests.CreateScene
import com.vivid.core.network.obs.requests.GetCurrentProgramScene
import com.vivid.core.network.obs.requests.GetInputList
import com.vivid.core.network.obs.requests.GetInputMute
import com.vivid.core.network.obs.requests.GetInputSettings
import com.vivid.core.network.obs.requests.GetSceneList
import com.vivid.core.network.obs.requests.GetVersion
import com.vivid.core.network.obs.requests.Request
import com.vivid.core.network.obs.requests.RequestType
import com.vivid.core.network.obs.requests.SetCurrentProgramScene
import com.vivid.core.network.obs.requests.SetInputMute
import com.vivid.core.network.obs.requests.SetInputSettings
import com.vivid.core.network.obs.requests.TakeSourceScreenshot
import com.vivid.core.network.obs.requests.ToggleInputMute
import com.vivid.core.network.obs.security.AuthenticationChallenge
import com.vivid.core.network.obs.security.AuthenticationResponse
import com.vivid.core.network.obs.security.generateAuthenticationString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OBSWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    companion object {
        // Event-Subscriptions für die OBS-Steuerung: General (1) | Inputs (8) |
        // InputVolumeMeters (8192) — letzteres liefert InputAudioLevelsChanged- und
        // InputMuteStateChanged-Events (OBS WebSocket 5.x EventSubscription).
        const val EVENT_SUBSCRIPTION_MASK = 8192 + 8 + 1
    }

    private var webSocket: WebSocket? = null
    private val requestIdCounter = AtomicInteger(1)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _inputs = MutableStateFlow<List<String>>(emptyList())
    val inputs: StateFlow<List<String>> = _inputs.asStateFlow()

    private val _muteStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val muteStates: StateFlow<Map<String, Boolean>> = _muteStates.asStateFlow()

    private val _audioLevels = MutableStateFlow<Map<String, Float>>(emptyMap())
    val audioLevels: StateFlow<Map<String, Float>> = _audioLevels.asStateFlow()

    private val _syncOffsets = MutableStateFlow<Map<String, Long>>(emptyMap())
    val syncOffsets: StateFlow<Map<String, Long>> = _syncOffsets.asStateFlow()

    private val _scenes = MutableStateFlow<List<String>>(emptyList())
    val scenes: StateFlow<List<String>> = _scenes.asStateFlow()

    private val _currentProgramScene = MutableStateFlow<String?>(null)
    val currentProgramScene: StateFlow<String?> = _currentProgramScene.asStateFlow()

    private val _snapshot = MutableStateFlow<ByteArray?>(null)
    val snapshot: StateFlow<ByteArray?> = _snapshot.asStateFlow()

    /**
     * Verbindet mit OBS. Standard ist [useTls] = false → `ws://` (OBS Studio
     * liefert ohne TLS-Konfiguration nur Klartext-WebSockets auf Port 4455).
     * Für Remote-Verbindungen mit TLS kann [useTls] auf true gesetzt werden → `wss://`.
     */
    fun connect(password: String, ip: String, port: Int, useTls: Boolean = false) {
        // Das Passwort wird NICHT als Klartext-Feld gespeichert, sondern nur
        // lokal an den Listener dieser Verbindung übergeben.
        val scheme = if (useTls) "wss" else "ws"
        val request = okhttp3.Request.Builder()
            .url("$scheme://$ip:$port")
            .build()

        val connectionPassword = password
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.d("WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.d("Received message: $text")
                handleMessage(text, connectionPassword)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("WebSocket closing: $reason")
                _isConnected.value = false
                resetState()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "WebSocket failure")
                _isConnected.value = false
                resetState()
            }
        }

        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
        resetState()
    }

    private fun handleMessage(message: String, password: String) {
        try {
            val opCode = gson.fromJson(message, Map::class.java)["op"] as? Double
            when (opCode?.toInt()) {
                0 -> handleHello(message, password)
                2 -> handleIdentified()
                5 -> handleEvent(message)
                7 -> handleRequestResponse(message)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing message")
        }
    }

    private fun handleHello(message: String, password: String) {
        if (password.isBlank()) {
            Timber.e("OBS Password is not set, cannot authenticate.")
            disconnect()
            return
        }

        val challenge = gson.fromJson(message, AuthenticationChallenge::class.java)
        challenge.d.authentication?.let {
            val authString = generateAuthenticationString(password, it.salt, it.challenge)
            val response = AuthenticationResponse(
                op = 1,
                d = AuthenticationResponse.Data(
                    rpcVersion = challenge.d.rpcVersion,
                    authentication = authString,
                    eventSubscriptions = EVENT_SUBSCRIPTION_MASK,
                ),
            )
            send(response)
        }
    }

    private fun handleIdentified() {
        _isConnected.value = true
        Timber.d("Successfully identified with OBS")
        sendRequest(GetVersion(), RequestType.GetVersion)
    }

    // Async-Events (OpCode 5): Audio-Levels, Mute- und Szenen-Änderungen.
    // Die übrigen Zustände (Inputs, Sync-Offset, Snapshot) werden via
    // Request-/Response zyklisch aufgefrischt.
    private fun handleEvent(message: String) {
        val d = gson.fromJson(message, JsonObject::class.java).getAsJsonObject("d")
        val eventType = d.get("eventType")?.asString ?: return
        val eventData = d.get("eventData")?.asJsonObject ?: return
        when (eventType) {
            "InputAudioLevelsChanged" -> parseAudioLevels(eventData)
            "InputMuteStateChanged" -> {
                val inputName = eventData.get("inputName")?.asString ?: return
                val inputMuted = eventData.get("inputMuted")?.asBoolean ?: return
                _muteStates.value = _muteStates.value + (inputName to inputMuted)
            }
            "CurrentProgramSceneChanged" -> {
                eventData.get("currentProgramSceneName")?.asString?.let { _currentProgramScene.value = it }
            }
            "SceneListChanged" -> refreshScenes()
        }
    }

    private fun parseAudioLevels(eventData: JsonObject) {
        val inputs = eventData.getAsJsonArray("inputs") ?: return
        val levels = mutableMapOf<String, Float>()
        inputs.forEach { element ->
            val input = element.asJsonObject
            val name = input.get("inputName")?.asString ?: return@forEach
            val dbs = input.getAsJsonArray("inputLevelsDb")
            val db = dbs?.firstOrNull()?.asFloat ?: return@forEach
            levels[name] = db
        }
        if (levels.isNotEmpty()) {
            _audioLevels.value = levels
        }
    }

    // Request-Responses (OpCode 7): Auswertung ausschließlich über den vom Server
    // gespiegelten `requestType` — die `responseData` trägt alle nötigen Feldnamen,
    // eine RequestId-Korrelation ist damit nicht erforderlich.
    private fun handleRequestResponse(message: String) {
        val root = gson.fromJson(message, JsonObject::class.java)
        val d = root.getAsJsonObject("d")
        val status = d.getAsJsonObject("requestStatus")
        val requestType = d.get("requestType")?.asString ?: return
        if (status?.get("result")?.asBoolean == false) {
            Timber.w("OBS request failed: $requestType — ${status.get("comment")?.asString}")
            return
        }
        val responseData = d.get("responseData")?.asJsonObject ?: return
        when (requestType) {
            RequestType.GetInputList.name -> parseInputList(responseData)
            RequestType.GetSceneList.name -> parseSceneList(responseData)
            RequestType.GetCurrentProgramScene.name -> {
                responseData.get("currentProgramSceneName")?.asString?.let {
                    _currentProgramScene.value = it
                }
            }
            RequestType.GetInputMute.name, RequestType.ToggleInputMute.name -> parseInputMute(responseData)
            RequestType.SetInputMute.name -> Unit // Toggle/Get/Events halten den Flow aktuell
            RequestType.SetCurrentProgramScene.name -> {
                responseData.get("sceneName")?.asString?.let { _currentProgramScene.value = it }
            }
            RequestType.GetInputSettings.name, RequestType.SetInputSettings.name -> parseInputSettings(responseData)
            RequestType.TakeSourceScreenshot.name -> parseScreenshot(responseData)
            RequestType.CreateScene.name, RequestType.CreateInput.name -> Unit // einmalige Aktionen
            else -> Timber.d("OBS response for unhandled requestType: $requestType")
        }
    }

    private fun parseInputList(responseData: JsonObject) {
        val array = responseData.getAsJsonArray("inputs") ?: return
        _inputs.value = array.mapNotNull { it.asJsonObject.get("inputName")?.asString }
    }

    private fun parseSceneList(responseData: JsonObject) {
        _currentProgramScene.value = responseData.get("currentProgramSceneName")?.asString
        val array = responseData.getAsJsonArray("scenes") ?: return
        _scenes.value = array.mapNotNull { it.asJsonObject.get("sceneName")?.asString }
    }

    private fun parseInputMute(responseData: JsonObject) {
        val inputName = responseData.get("inputName")?.asString ?: return
        val inputMuted = responseData.get("inputMuted")?.asBoolean ?: return
        _muteStates.value = _muteStates.value + (inputName to inputMuted)
    }

    private fun parseInputSettings(responseData: JsonObject) {
        val inputName = responseData.get("inputName")?.asString ?: return
        val syncOffset = responseData.getAsJsonObject("inputSettings")?.get("syncOffset")?.asLong
        if (syncOffset != null) {
            _syncOffsets.value = _syncOffsets.value + (inputName to syncOffset)
        }
    }

    private fun parseScreenshot(responseData: JsonObject) {
        val img = responseData.get("img")?.asString ?: run {
            Timber.w("OBS screenshot response without img")
            return
        }
        ObsBase64.decode(img)?.let { _snapshot.value = it }
    }

    fun sendRequest(request: Request, requestType: RequestType) {
        val requestWithId = request.toRequestWithId(
            requestId = requestIdCounter.getAndIncrement().toString(),
            requestType = requestType,
        )
        send(requestWithId)
    }

    // --- OBS-Steuerungs-Aktionen (PARITY Row 71) ---

    fun refreshInputs() {
        sendRequest(GetInputList(), RequestType.GetInputList)
    }

    fun refreshScenes() {
        sendRequest(GetSceneList(), RequestType.GetSceneList)
    }

    fun refreshProgramScene() {
        sendRequest(GetCurrentProgramScene(), RequestType.GetCurrentProgramScene)
    }

    fun toggleMute(inputName: String) {
        sendRequest(ToggleInputMute(inputName), RequestType.ToggleInputMute)
    }

    fun setMute(inputName: String, muted: Boolean) {
        sendRequest(SetInputMute(inputName, muted), RequestType.SetInputMute)
    }

    fun refreshSyncOffset(inputName: String) {
        sendRequest(GetInputSettings(inputName), RequestType.GetInputSettings)
    }

    fun setSyncOffset(inputName: String, syncOffsetNs: Long) {
        sendRequest(
            SetInputSettings(inputName, mapOf("syncOffset" to syncOffsetNs)),
            RequestType.SetInputSettings,
        )
    }

    fun setProgramScene(sceneName: String) {
        _currentProgramScene.value = sceneName
        sendRequest(SetCurrentProgramScene(sceneName), RequestType.SetCurrentProgramScene)
    }

    fun createScene(sceneName: String) {
        sendRequest(CreateScene(sceneName), RequestType.CreateScene)
    }

    fun createBlackoutInput(sceneName: String, inputName: String) {
        sendRequest(
            CreateInput(
                sceneName = sceneName,
                inputName = inputName,
                inputKind = "color_source_v3",
                inputSettings = mapOf("color" to 0xFF000000L),
                sceneItemEnabled = true,
            ),
            RequestType.CreateInput,
        )
    }

    fun takeScreenshot(sourceName: String, width: Int? = null, height: Int? = null) {
        sendRequest(
            TakeSourceScreenshot(sourceName = sourceName, imageFormat = "png", imageWidth = width, imageHeight = height),
            RequestType.TakeSourceScreenshot,
        )
    }

    private fun resetState() {
        _inputs.value = emptyList()
        _muteStates.value = emptyMap()
        _audioLevels.value = emptyMap()
        _syncOffsets.value = emptyMap()
        _scenes.value = emptyList()
        _currentProgramScene.value = null
        _snapshot.value = null
    }

    private fun send(data: Any) {
        try {
            webSocket?.send(gson.toJson(data))
            // Bewusst ohne Payload loggen: Nachrichten wie die Identify-Antwort
            // enthalten den aus dem Passwort abgeleiteten Auth-String.
            Timber.d("Sent WebSocket message")
        } catch (e: Exception) {
            Timber.e(e, "Error sending message")
        }
    }
}