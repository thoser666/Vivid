package com.vivid.core.network.obs

import com.google.gson.Gson
import com.vivid.core.network.KtorClientFactory
import com.vivid.core.network.awaitPortListening
import com.vivid.core.network.obs.requests.GetVersion
import com.vivid.core.network.obs.requests.RequestType
import io.ktor.client.HttpClient
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Deckt das PARITY-Row-71-Verhalten des Clients ab: Request-/Response-Korrelation
 * (OpCode 7), Event-Verarbeitung (OpCode 5) und die Steuerungs-Aktionen — über
 * echte WebSocket-Frames gegen einen lokalen Ktor-Testserver (Transport wie in
 * Produktion, Issue #226). Der Server sendet Hello + Identify-Bestätigung
 * automatisch; Test-Frames gelangen via [receive] in die Client-Session.
 */
// JUnit-Timeout (interrupt-basiert): Ein E2E-Hang darf den Gradle-Test-Worker
// nie ewig blockieren — der Test bricht mit Stacktrace ab (Issue #226).
@Timeout(30)
class OBSWebSocketClientControlTest {

    private var server: EmbeddedServer<*, *>? = null
    private var port = 0
    private val serverReceived = CopyOnWriteArrayList<String>()
    private val toClient = MutableSharedFlow<String>(extraBufferCapacity = 64)
    private val gson = Gson()
    private var lastHttpClient: HttpClient? = null
    private lateinit var client: OBSWebSocketClient

    @BeforeEach
    fun setUp() {
        serverReceived.clear()
        // Freien Port vorab reservieren (resolvedConnectors steht in Ktor 3.6
        // nicht zur Verfuegung); die Reservierung ist sofort wieder frei.
        val freePort = java.net.ServerSocket(0).use { it.localPort }
        val s = embeddedServer(CIO, port = freePort, host = "127.0.0.1") {
            install(WebSockets)
            routing {
                webSocket("/") {
                    send(Frame.Text("""{"op":0,"d":{"rpcVersion":1,"authentication":{"challenge":"C1","salt":"S1"}}}"""))
                    launch { toClient.collect { send(Frame.Text(it)) } }
                    for (frame in incoming) {
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        serverReceived += text
                        if (text.contains("\"op\":1")) send(Frame.Text("""{"op":2}"""))
                    }
                }
            }
        }
        s.start(wait = false)
        port = freePort
        server = s
        // #264: CIO bindet asynchron, start(wait = false) kehrt vorher zurueck.
        // Ohne diese Schleife laeuft der connect() bei Last ins Leere und
        // der Test scheitert am Transport statt am geprueften Verhalten.
        check(awaitPortListening(freePort)) {
            "CIO-Server hat den Port $freePort nicht innerhalb von 5s gebunden"
        }
        val http = KtorClientFactory.create().also { lastHttpClient = it }
        client = OBSWebSocketClient(http, gson)
        client.connect("pw", "127.0.0.1", port)
        // Blockierendes Polling statt Coroutine-Await: setUp bleibt frei von
        // runBlocking — der JUnit-Lifecycle-Thread parkt nie auf Coroutine-
        // Joining (Hang-Forensik: der runBlocking-Body lief komplett durch,
        // nur seine Rueckkehr kehrte nie zurueck; Issue #226).
        val deadline = System.currentTimeMillis() + 20_000
        while (!client.isConnected.value) {
            check(System.currentTimeMillis() < deadline) { "Handshake im SetUp misslungen" }
            Thread.sleep(25)
        }
    }

    @AfterEach
    fun tearDown() {
        // Alle nicht-daemonisierten Transport-Threads freigeben, sonst haengt
        // der Gradle-Test-Worker: Der CIO-Client-Threadpool ist kein Daemon und
        // ueberlebt ohne close() den JVM-Exit (Issue #226).
        runCatching { client.shutdown() }
        runCatching { lastHttpClient?.close() }
        runCatching { server?.stop(gracePeriodMillis = 100, timeoutMillis = 1_000) }
        server = null
    }

    /** Spielt eine Server-Nachricht in die Client-Session ein (nicht-suspendierend). */
    private fun receive(message: String) {
        check(toClient.tryEmit(message)) { "toClient buffer voll — Nachricht verworfen" }
    }

    /** Blockierendes Polling (kein Coroutine-Await): vermeidet runBlocking-Bridges. */
    private fun awaitCondition(timeoutMs: Long = 3_000, cond: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return true
            Thread.sleep(25)
        }
        return cond()
    }

    @Test
    fun `identify request subscribes to inputs and volume meter events`() {
        val identify = serverReceived.first { it.contains("\"op\":1") }
        assertTrue(identify.contains("\"eventSubscriptions\":${OBSWebSocketClient.EVENT_SUBSCRIPTION_MASK}"))
        assertEquals(8192 + 8 + 1, OBSWebSocketClient.EVENT_SUBSCRIPTION_MASK)
    }

    @Test
    fun `get input list response populates the inputs flow`() {
        receive(
            """{"op":7,"d":{"requestType":"GetInputList","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"inputs":[{"inputName":"Mic/Aux"},{"inputName":"Desktop Audio"}]}}}""",
        )
        assertTrue(awaitCondition { client.inputs.value.isNotEmpty() })
        assertEquals(listOf("Mic/Aux", "Desktop Audio"), client.inputs.value)
    }

    @Test
    fun `get scene list response populates scenes and current scene`() {
        receive(
            """{"op":7,"d":{"requestType":"GetSceneList","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"currentProgramSceneName":"Live","scenes":[{"sceneName":"Live"},{"sceneName":"Intro"}]}}}""",
        )
        assertTrue(awaitCondition { client.scenes.value.isNotEmpty() })
        assertEquals(listOf("Live", "Intro"), client.scenes.value)
        assertEquals("Live", client.currentProgramScene.value)
    }

    @Test
    fun `get current program scene response updates the scene flow`() {
        receive(
            """{"op":7,"d":{"requestType":"GetCurrentProgramScene","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"currentProgramSceneName":"Live"}}}""",
        )
        assertTrue(awaitCondition { client.currentProgramScene.value == "Live" })
        assertEquals("Live", client.currentProgramScene.value)
    }

    @Test
    fun `toggle input mute response updates the mute flow`() {
        receive(
            """{"op":7,"d":{"requestType":"ToggleInputMute","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"inputName":"Mic/Aux","inputMuted":true}}}""",
        )
        assertTrue(awaitCondition { client.muteStates.value.isNotEmpty() })
        assertEquals(mapOf("Mic/Aux" to true), client.muteStates.value)
    }

    @Test
    fun `mute state change event updates the mute flow`() {
        receive(
            """{"op":5,"d":{"eventType":"InputMuteStateChanged","eventData":{"inputName":"Desktop Audio","inputMuted":false}}}""",
        )
        assertTrue(awaitCondition { client.muteStates.value.isNotEmpty() })
        assertEquals(mapOf("Desktop Audio" to false), client.muteStates.value)
    }

    @Test
    fun `program scene change event updates the scene flow`() {
        receive(
            """{"op":5,"d":{"eventType":"CurrentProgramSceneChanged","eventData":{"sceneName":"Intro","currentProgramSceneName":"Intro"}}}""",
        )
        assertTrue(awaitCondition { client.currentProgramScene.value == "Intro" })
        assertEquals("Intro", client.currentProgramScene.value)
    }

    @Test
    fun `scene list change event triggers a scene refresh request`() {
        receive("""{"op":5,"d":{"eventType":"SceneListChanged","eventData":{"scenes":[{"sceneName":"Live"}]}}}""")
        assertTrue(awaitCondition { serverReceived.any { it.contains("\"requestType\":\"GetSceneList\"") } })
    }

    @Test
    fun `audio levels event populates the levels flow with first db value`() {
        receive(
            """{"op":5,"d":{"eventType":"InputAudioLevelsChanged","eventData":{"inputs":[""" +
                """{"inputName":"Mic/Aux","inputLevelsMul":[0.4,0.4],"inputLevelsDb":[-12.5,-12.5]},""" +
                """{"inputName":"Desktop Audio","inputLevelsMul":[0.9],"inputLevelsDb":[-2.1]}]}}}""",
        )
        assertTrue(awaitCondition { client.audioLevels.value.isNotEmpty() })
        assertEquals(mapOf("Mic/Aux" to -12.5f, "Desktop Audio" to -2.1f), client.audioLevels.value)
    }

    @Test
    fun `get input settings response populates the sync offset flow`() {
        receive(
            """{"op":7,"d":{"requestType":"GetInputSettings","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"inputName":"Mic/Aux","inputKind":"wasapi_input_capture","inputSettings":{"syncOffset":50000000}}}}""",
        )
        assertTrue(awaitCondition { client.syncOffsets.value.isNotEmpty() })
        assertEquals(mapOf("Mic/Aux" to 50_000_000L), client.syncOffsets.value)
    }

    @Test
    fun `take source screenshot response decodes base64 into the snapshot flow`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        receive(
            """{"op":7,"d":{"requestType":"TakeSourceScreenshot","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"img":"${ObsBase64.encode(bytes)}"}}}""",
        )
        assertTrue(awaitCondition { client.snapshot.value != null })
        assertArrayEquals(bytes, client.snapshot.value)
    }

    @Test
    fun `failed request does not alter flows`() {
        receive(
            """{"op":7,"d":{"requestType":"GetInputList","requestStatus":{"result":false,"code":204,"comment":"not found"},""" +
                """"responseData":{}}}""",
        )
        // Kurze Ausbreitungszeit, dann: nichts verändert.
        awaitCondition(timeoutMs = 500) { false }
        assertEquals(emptyList<String>(), client.inputs.value)
        assertNull(client.currentProgramScene.value)
    }

    @Test
    fun `unknown event type is ignored`() {
        receive("""{"op":5,"d":{"eventType":"SceneCreated","eventData":{"sceneName":"X"}}}""")
        awaitCondition(timeoutMs = 500) { false }
        assertEquals(emptyList<String>(), client.scenes.value)
    }

    @Test
    fun `toggle mute sends the toggle request with the input name`() {
        client.toggleMute("Mic/Aux")
        assertTrue(
            awaitCondition {
                serverReceived.any { it.contains("\"requestType\":\"ToggleInputMute\"") && it.contains("\"inputName\":\"Mic/Aux\"") }
            },
        )
    }

    @Test
    fun `set sync offset sends the delta as input setting`() {
        client.setSyncOffset("Mic/Aux", 50_000_000L)
        assertTrue(
            awaitCondition {
                serverReceived.any { it.contains("\"requestType\":\"SetInputSettings\"") && it.contains("\"syncOffset\":50000000") }
            },
        )
    }

    @Test
    fun `set program scene updates the flow optimistically and sends the request`() {
        client.setProgramScene("Intro")
        assertEquals("Intro", client.currentProgramScene.value)
        assertTrue(
            awaitCondition {
                serverReceived.any { it.contains("\"requestType\":\"SetCurrentProgramScene\"") && it.contains("\"sceneName\":\"Intro\"") }
            },
        )
    }

    @Test
    fun `create scene sends the create request`() {
        client.createScene("Vivid Blackout")
        assertTrue(
            awaitCondition { serverReceived.any { it.contains("\"requestType\":\"CreateScene\"") && it.contains("\"sceneName\":\"Vivid Blackout\"") } },
        )
    }

    @Test
    fun `create blackout input sends a black color source`() {
        client.createBlackoutInput("Vivid Blackout", "Blackout")
        assertTrue(
            awaitCondition {
                serverReceived.any {
                    it.contains("\"requestType\":\"CreateInput\"") &&
                        it.contains("\"inputKind\":\"color_source_v3\"") &&
                        it.contains("\"color\":4278190080") &&
                        it.contains("\"sceneItemEnabled\":true")
                }
            },
        )
    }

    @Test
    fun `take screenshot sends the correct format and source`() {
        client.takeScreenshot("Live")
        assertTrue(
            awaitCondition {
                serverReceived.any {
                    it.contains("\"requestType\":\"TakeSourceScreenshot\"") &&
                        it.contains("\"sourceName\":\"Live\"") &&
                        it.contains("\"imageFormat\":\"png\"")
                }
            },
        )
    }

    @Test
    fun `disconnect resets the control flows`() {
        receive(
            """{"op":7,"d":{"requestType":"GetInputList","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"inputs":[{"inputName":"Mic/Aux"}]}}}""",
        )
        assertTrue(awaitCondition { client.inputs.value.isNotEmpty() })

        client.disconnect()

        assertTrue(awaitCondition { !client.isConnected.value })
        assertTrue(client.inputs.value.isEmpty())
        assertEquals(emptyMap<String, Boolean>(), client.muteStates.value)
        assertNull(client.currentProgramScene.value)
        assertNull(client.snapshot.value)
    }

    @Test
    fun `sendRequest still increments request ids`() {
        client.sendRequest(GetVersion(), RequestType.GetVersion)
        client.sendRequest(GetVersion(), RequestType.GetVersion)

        // Der Identify-Handshake im SetUp verbraucht requestId 1 (GetVersion),
        // die beiden expliziten Requests tragen daher 2 und 3.
        assertTrue(awaitCondition { serverReceived.count { it.contains("\"requestType\":\"GetVersion\"") } >= 3 })
        assertTrue(serverReceived.any { it.contains("\"requestId\":\"2\"") })
        assertTrue(serverReceived.any { it.contains("\"requestId\":\"3\"") })
    }
}
