package com.vivid.core.network.obs

import com.google.gson.Gson
import com.vivid.core.network.obs.requests.GetVersion
import com.vivid.core.network.obs.requests.RequestType
import com.vivid.core.network.obs.security.AuthenticationResponse
import com.vivid.core.network.obs.security.generateAuthenticationString
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OBSWebSocketClientTest {

    private val okHttpClient = mockk<OkHttpClient>()
    private val webSocket = mockk<WebSocket>()
    private val client = OBSWebSocketClient(okHttpClient, Gson())

    private val listenerSlot = slot<WebSocketListener>()

    @BeforeEach
    fun setUp() {
        every { okHttpClient.newWebSocket(any(), capture(listenerSlot)) } returns webSocket
        every { webSocket.send(any<String>()) } returns true
        every { webSocket.close(any(), any()) } returns true
    }

    @Test
    fun `isConnected is initially false`() {
        assertFalse(client.isConnected.value)
    }

    @Test
    fun `connect opens a websocket to the given address`() {
        val requestSlot = slot<Request>()

        client.connect("secret", "127.0.0.1", 4455)

        verify { okHttpClient.newWebSocket(capture(requestSlot), any()) }
        // OkHttp normalizes the URL (scheme ws->http, trailing slash); check host and port.
        assertEquals("127.0.0.1", requestSlot.captured.url.host)
        assertEquals(4455, requestSlot.captured.url.port)
    }

    @Test
    fun `hello message triggers an identify response with the auth string`() {
        val salt = "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6"
        val challenge = "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6"
        client.connect("mypassword", "127.0.0.1", 4455)

        listenerSlot.captured.onMessage(
            webSocket,
            """{"op":0,"d":{"rpcVersion":1,"authentication":{"challenge":"$challenge","salt":"$salt"}}}""",
        )

        val sentJson = slot<String>()
        verify { webSocket.send(capture(sentJson)) }

        // Parse the payload back and verify the authentication response structurally.
        val response = Gson().fromJson(sentJson.captured, AuthenticationResponse::class.java)
        assertEquals(1, response.op)
        assertEquals(
            generateAuthenticationString("mypassword", salt, challenge),
            response.d.authentication,
        )
    }

    @Test
    fun `hello without saved password disconnects instead of authenticating`() {
        val salt = "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6"
        val challenge = "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6"
        // Connect with a blank password, so the listener is registered but the password stays blank.
        client.connect("", "127.0.0.1", 4455)

        listenerSlot.captured.onMessage(
            webSocket,
            """{"op":0,"d":{"rpcVersion":1,"authentication":{"challenge":"$challenge","salt":"$salt"}}}""",
        )

        verify { webSocket.close(1000, "User disconnected") }
        assertFalse(client.isConnected.value)
    }

    @Test
    fun `identified message marks the client as connected and requests the version`() {
        client.connect("pw", "127.0.0.1", 4455)

        listenerSlot.captured.onMessage(webSocket, """{"op":2}""")

        assertTrue(client.isConnected.value)
        verify {
            webSocket.send(match<String> { it.contains("\"op\":6") && it.contains("\"requestType\":\"GetVersion\"") })
        }
    }

    @Test
    fun `websocket failure resets the connected state`() {
        client.connect("pw", "127.0.0.1", 4455)
        listenerSlot.captured.onMessage(webSocket, """{"op":2}""")
        assertTrue(client.isConnected.value)

        listenerSlot.captured.onFailure(webSocket, RuntimeException("boom"), null)

        assertFalse(client.isConnected.value)
    }

    @Test
    fun `disconnect closes the socket and resets the connected state`() {
        client.connect("pw", "127.0.0.1", 4455)
        listenerSlot.captured.onMessage(webSocket, """{"op":2}""")
        assertTrue(client.isConnected.value)

        client.disconnect()

        verify { webSocket.close(1000, "User disconnected") }
        assertFalse(client.isConnected.value)
    }

    @Test
    fun `sendRequest sends a version request with an incrementing request id`() {
        client.connect("pw", "127.0.0.1", 4455)

        client.sendRequest(GetVersion(), RequestType.GetVersion)

        verify {
            webSocket.send(match<String> { it.contains("\"requestId\":\"1\"") })
        }
    }
}
