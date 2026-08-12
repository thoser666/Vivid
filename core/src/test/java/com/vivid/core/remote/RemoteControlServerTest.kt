package com.vivid.core.remote

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val TOKEN = "secret-token-123"

/** Test-Double für StreamControl, das Start/Stop zählt und den Status liefert. */
private class FakeStreamControl : StreamControl {
    override val status = MutableStateFlow(RemoteStreamStatus.IDLE)
    var startCount = 0
    var stopCount = 0
    override suspend fun start() {
        startCount++
        status.value = RemoteStreamStatus.STREAMING
    }

    override fun stop() {
        stopCount++
        status.value = RemoteStreamStatus.IDLE
    }
}

class RemoteControlServerTest {

    @Test
    fun `status endpoint returns the current stream status`() = testApplication {
        application {
            remoteControlModule(FakeStreamControl(), TOKEN)
        }
        val response = client.get("/status")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"IDLE\""))
    }

    @Test
    fun `status endpoint reflects streaming state after start`() = testApplication {
        val control = FakeStreamControl()
        control.status.value = RemoteStreamStatus.STREAMING
        application {
            remoteControlModule(control, TOKEN)
        }
        val response = client.get("/status")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"STREAMING\""))
    }

    @Test
    fun `start without token is rejected`() = testApplication {
        val control = FakeStreamControl()
        application {
            remoteControlModule(control, TOKEN)
        }
        val response = client.post("/start")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, control.startCount)
    }

    @Test
    fun `start with wrong token is rejected`() = testApplication {
        val control = FakeStreamControl()
        application {
            remoteControlModule(control, TOKEN)
        }
        val response = client.post("/start") {
            header(HttpHeaders.Authorization, "Bearer wrong-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, control.startCount)
    }

    @Test
    fun `start with correct token starts the stream`() = testApplication {
        val control = FakeStreamControl()
        application {
            remoteControlModule(control, TOKEN)
        }
        val response = client.post("/start") {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, control.startCount)
    }

    @Test
    fun `stop with correct token stops the stream`() = testApplication {
        val control = FakeStreamControl()
        application {
            remoteControlModule(control, TOKEN)
        }
        val response = client.post("/stop") {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, control.stopCount)
    }

    @Test
    fun `unknown route returns not found`() = testApplication {
        application {
            remoteControlModule(FakeStreamControl(), TOKEN)
        }
        val response = client.get("/unknown")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
