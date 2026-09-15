package com.vivid.core.network.obs

import com.google.gson.Gson
import com.vivid.core.network.obs.requests.GetVersion
import com.vivid.core.network.obs.requests.RequestType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Deckt das PARITY-Row-71-Verhalten des Clients ab: Request-/Response-Korrelation
 * (OpCode 7), Event-Verarbeitung (OpCode 5) und die neuen Steuerungs-Aktionen.
 */
class OBSWebSocketClientControlTest {

    private val okHttpClient = mockk<OkHttpClient>()
    private val webSocket = mockk<WebSocket>()
    private val client = OBSWebSocketClient(okHttpClient, Gson())

    private val listenerSlot = slot<WebSocketListener>()

    @BeforeEach
    fun setUp() {
        every { okHttpClient.newWebSocket(any(), capture(listenerSlot)) } returns webSocket
        every { webSocket.send(any<String>()) } returns true
        every { webSocket.close(any(), any()) } returns true
        client.connect("pw", "127.0.0.1", 4455)
    }

    private fun receive(message: String) {
        listenerSlot.captured.onMessage(webSocket, message)
    }

    @Test
    fun `identify request subscribes to inputs and volume meter events`() {
        val salt = "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6"
        val challenge = "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6"
        receive("""{"op":0,"d":{"rpcVersion":1,"authentication":{"challenge":"$challenge","salt":"$salt"}}}""")

        val sentJson = slot<String>()
        verify { webSocket.send(capture(sentJson)) }
        assertTrue(sentJson.captured.contains("\"eventSubscriptions\":${OBSWebSocketClient.EVENT_SUBSCRIPTION_MASK}"))
        assertEquals(8192 + 8 + 1, OBSWebSocketClient.EVENT_SUBSCRIPTION_MASK)
    }

    @Test
    fun `get input list response populates the inputs flow`() {
        receive(
            """{"op":7,"d":{"requestType":"GetInputList","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"inputs":[{"inputName":"Mic/Aux"},{"inputName":"Desktop Audio"}]}}}""",
        )

        assertEquals(listOf("Mic/Aux", "Desktop Audio"), client.inputs.value)
    }

    @Test
    fun `get scene list response populates scenes and current scene`() {
        receive(
            """{"op":7,"d":{"requestType":"GetSceneList","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"currentProgramSceneName":"Live","scenes":[{"sceneName":"Live"},{"sceneName":"Intro"}]}}}""",
        )

        assertEquals(listOf("Live", "Intro"), client.scenes.value)
        assertEquals("Live", client.currentProgramScene.value)
    }

    @Test
    fun `get current program scene response updates the scene flow`() {
        receive(
            """{"op":7,"d":{"requestType":"GetCurrentProgramScene","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"currentProgramSceneName":"Live"}}}""",
        )

        assertEquals("Live", client.currentProgramScene.value)
    }

    @Test
    fun `toggle input mute response updates the mute flow`() {
        receive(
            """{"op":7,"d":{"requestType":"ToggleInputMute","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"inputName":"Mic/Aux","inputMuted":true}}}""",
        )

        assertEquals(mapOf("Mic/Aux" to true), client.muteStates.value)
    }

    @Test
    fun `mute state change event updates the mute flow`() {
        receive(
            """{"op":5,"d":{"eventType":"InputMuteStateChanged","eventData":{"inputName":"Desktop Audio","inputMuted":false}}}""",
        )

        assertEquals(mapOf("Desktop Audio" to false), client.muteStates.value)
    }

    @Test
    fun `program scene change event updates the scene flow`() {
        receive(
            """{"op":5,"d":{"eventType":"CurrentProgramSceneChanged","eventData":{"sceneName":"Intro","currentProgramSceneName":"Intro"}}}""",
        )

        assertEquals("Intro", client.currentProgramScene.value)
    }

    @Test
    fun `scene list change event triggers a scene refresh request`() {
        receive("""{"op":5,"d":{"eventType":"SceneListChanged","eventData":{"scenes":[{"sceneName":"Live"}]}}}""")

        verify { webSocket.send(match<String> { it.contains("\"requestType\":\"GetSceneList\"") }) }
    }

    @Test
    fun `audio levels event populates the levels flow with first db value`() {
        receive(
            """{"op":5,"d":{"eventType":"InputAudioLevelsChanged","eventData":{"inputs":[""" +
                """{"inputName":"Mic/Aux","inputLevelsMul":[0.4,0.4],"inputLevelsDb":[-12.5,-12.5]},""" +
                """{"inputName":"Desktop Audio","inputLevelsMul":[0.9],"inputLevelsDb":[-2.1]}]}}}""",
        )

        assertEquals(mapOf("Mic/Aux" to -12.5f, "Desktop Audio" to -2.1f), client.audioLevels.value)
    }

    @Test
    fun `get input settings response populates the sync offset flow`() {
        receive(
            """{"op":7,"d":{"requestType":"GetInputSettings","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"inputName":"Mic/Aux","inputKind":"wasapi_input_capture","inputSettings":{"syncOffset":50000000}}}}""" .
                trimEnd(),
        )

        assertEquals(mapOf("Mic/Aux" to 50_000_000L), client.syncOffsets.value)
    }

    @Test
    fun `take source screenshot response decodes base64 into the snapshot flow`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        receive(
            """{"op":7,"d":{"requestType":"TakeSourceScreenshot","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"img":"${ObsBase64.encode(bytes)}"}}}""",
        )

        assertArrayEquals(bytes, client.snapshot.value)
    }

    @Test
    fun `failed request does not alter flows`() {
        receive(
            """{"op":7,"d":{"requestType":"GetInputList","requestStatus":{"result":false,"code":204,"comment":"not found"},""" +
                """"responseData":{}}}""",
        )

        assertEquals(emptyList<String>(), client.inputs.value)
        assertNull(client.currentProgramScene.value)
    }

    @Test
    fun `unknown event type is ignored`() {
        receive("""{"op":5,"d":{"eventType":"SceneCreated","eventData":{"sceneName":"X"}}}""")

        assertEquals(emptyList<String>(), client.scenes.value)
    }

    @Test
    fun `toggle mute sends the toggle request with the input name`() {
        client.toggleMute("Mic/Aux")

        verify {
            webSocket.send(match<String> { it.contains("\"requestType\":\"ToggleInputMute\"") && it.contains("\"inputName\":\"Mic/Aux\"") })
        }
    }

    @Test
    fun `set sync offset sends the delta as input setting`() {
        client.setSyncOffset("Mic/Aux", 50_000_000L)

        verify {
            webSocket.send(match<String> { it.contains("\"requestType\":\"SetInputSettings\"") && it.contains("\"syncOffset\":50000000") })
        }
    }

    @Test
    fun `set program scene updates the flow optimistically and sends the request`() {
        client.setProgramScene("Intro")

        assertEquals("Intro", client.currentProgramScene.value)
        verify { webSocket.send(match<String> { it.contains("\"requestType\":\"SetCurrentProgramScene\"") && it.contains("\"sceneName\":\"Intro\"") }) }
    }

    @Test
    fun `create scene sends the create request`() {
        client.createScene("Vivid Blackout")

        verify { webSocket.send(match<String> { it.contains("\"requestType\":\"CreateScene\"") && it.contains("\"sceneName\":\"Vivid Blackout\"") }) }
    }

    @Test
    fun `create blackout input sends a black color source`() {
        client.createBlackoutInput("Vivid Blackout", "Blackout")

        verify {
            webSocket.send(
                match<String> {
                    it.contains("\"requestType\":\"CreateInput\"") &&
                        it.contains("\"inputKind\":\"color_source_v3\"") &&
                        it.contains("\"color\":4278190080") &&
                        it.contains("\"sceneItemEnabled\":true")
                },
            )
        }
    }

    @Test
    fun `take screenshot sends the correct format and source`() {
        client.takeScreenshot("Live")

        verify {
            webSocket.send(
                match<String> {
                    it.contains("\"requestType\":\"TakeSourceScreenshot\"") &&
                        it.contains("\"sourceName\":\"Live\"") &&
                        it.contains("\"imageFormat\":\"png\"")
                },
            )
        }
    }

    @Test
    fun `disconnect resets the control flows`() {
        receive(
            """{"op":7,"d":{"requestType":"GetInputList","requestStatus":{"result":true,"code":100},""" +
                """"responseData":{"inputs":[{"inputName":"Mic/Aux"}]}}}""",
        )
        assertFalse(client.inputs.value.isEmpty())

        client.disconnect()

        assertTrue(client.inputs.value.isEmpty())
        assertEquals(emptyMap<String, Boolean>(), client.muteStates.value)
        assertNull(client.currentProgramScene.value)
        assertNull(client.snapshot.value)
    }

    @Test
    fun `sendRequest still increments request ids`() {
        client.sendRequest(GetVersion(), RequestType.GetVersion)
        client.sendRequest(GetVersion(), RequestType.GetVersion)

        verify {
            webSocket.send(match<String> { it.contains("\"requestId\":\"2\"") })
        }
    }
}