package com.vivid.core.remote

import com.vivid.core.log.LogEntry
import com.vivid.core.log.LogLevel
import com.vivid.core.log.LogStore
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
import org.junit.jupiter.api.io.TempDir
import java.io.File

private const val TOKEN = "secret-token-123"

// Wiederverwendete Pfade/Testdaten als Konstanten (DeepSource KT-W1042: keine
// mehrfach wiederholten String-Literale innerhalb einer Datei).
private const val PATH_START = "/start"
private const val PATH_LOGS = "/logs"
private const val MSG_TODAY = "today-entry"
private const val MSG_OLD = "old-entry"

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

    @TempDir
    lateinit var tempDir: File

    private fun newLogStore(dirName: String = "logs"): LogStore =
        LogStore(File(tempDir, dirName))

    private fun seed(store: LogStore, daysAgo: Int, message: String) {
        store.add(
            LogEntry(
                timestampMillis = System.currentTimeMillis() - daysAgo * 24L * 60 * 60 * 1000,
                level = LogLevel.INFO,
                tag = "Test",
                message = message,
            )
        )
    }

    @Test
    fun `status endpoint returns the current stream status`() = testApplication {
        application {
            remoteControlModule(FakeStreamControl(), TOKEN, newLogStore())
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
            remoteControlModule(control, TOKEN, newLogStore())
        }
        val response = client.get("/status")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"STREAMING\""))
    }

    @Test
    fun `start without token is rejected`() = testApplication {
        val control = FakeStreamControl()
        application {
            remoteControlModule(control, TOKEN, newLogStore())
        }
        val response = client.post(PATH_START)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, control.startCount)
    }

    @Test
    fun `start with wrong token is rejected`() = testApplication {
        val control = FakeStreamControl()
        application {
            remoteControlModule(control, TOKEN, newLogStore())
        }
        val response = client.post(PATH_START) {
            header(HttpHeaders.Authorization, "Bearer wrong-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, control.startCount)
    }

    @Test
    fun `start with correct token starts the stream`() = testApplication {
        val control = FakeStreamControl()
        application {
            remoteControlModule(control, TOKEN, newLogStore())
        }
        val response = client.post(PATH_START) {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, control.startCount)
    }

    @Test
    fun `stop with correct token stops the stream`() = testApplication {
        val control = FakeStreamControl()
        application {
            remoteControlModule(control, TOKEN, newLogStore())
        }
        val response = client.post("/stop") {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, control.stopCount)
    }

    // ── GET /logs ────────────────────────────────────────────────────────────

    @Test
    fun `logs without token is rejected and store is not read`() = testApplication {
        application {
            remoteControlModule(FakeStreamControl(), TOKEN, newLogStore())
        }
        val response = client.get(PATH_LOGS)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `logs with wrong token is rejected`() = testApplication {
        application {
            remoteControlModule(FakeStreamControl(), TOKEN, newLogStore())
        }
        val response = client.get(PATH_LOGS) {
            header(HttpHeaders.Authorization, "Bearer wrong-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `logs returns entries from the log store as json`() = testApplication {
        val store = newLogStore("logs_json")
        seed(store, daysAgo = 0, message = "hello today")
        application {
            remoteControlModule(FakeStreamControl(), TOKEN, store)
        }
        val response = client.get(PATH_LOGS) {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"days\":1"))
        assertTrue(body.contains("\"count\":1"))
        assertTrue(body.contains("hello today"))
    }

    @Test
    fun `logs default days excludes entries older than yesterday`() = testApplication {
        val store = newLogStore("logs_default")
        seed(store, daysAgo = 0, message = MSG_TODAY)
        seed(store, daysAgo = 3, message = MSG_OLD)
        application {
            remoteControlModule(FakeStreamControl(), TOKEN, store)
        }
        val response = client.get(PATH_LOGS) {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        val body = response.bodyAsText()
        assertTrue(body.contains(MSG_TODAY))
        assertTrue(!body.contains(MSG_OLD))
    }

    @Test
    fun `logs with days parameter widens the window`() = testApplication {
        val store = newLogStore("logs_days")
        seed(store, daysAgo = 0, message = MSG_TODAY)
        seed(store, daysAgo = 3, message = MSG_OLD)
        application {
            remoteControlModule(FakeStreamControl(), TOKEN, store)
        }
        val response = client.get("$PATH_LOGS?days=7") {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        val body = response.bodyAsText()
        assertTrue(body.contains("\"days\":7"))
        assertTrue(body.contains(MSG_TODAY))
        assertTrue(body.contains(MSG_OLD))
    }

    @Test
    fun `logs clamps days above the maximum`() = testApplication {
        val store = newLogStore("logs_clamp_high")
        seed(store, daysAgo = 0, message = MSG_TODAY)
        application {
            remoteControlModule(FakeStreamControl(), TOKEN, store)
        }
        val response = client.get("$PATH_LOGS?days=5000") {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"days\":${RemoteControlServer.MAX_LOG_DAYS}"))
    }

    @Test
    fun `logs clamps invalid or negative days to the default window`() = testApplication {
        val store = newLogStore("logs_invalid")
        seed(store, daysAgo = 0, message = MSG_TODAY)
        application {
            remoteControlModule(FakeStreamControl(), TOKEN, store)
        }
        listOf("?days=abc", "?days=-5").forEach { query ->
            val response = client.get("$PATH_LOGS$query") {
                header(HttpHeaders.Authorization, "Bearer $TOKEN")
            }
            assertEquals(HttpStatusCode.OK, response.status, "query=$query")
            assertTrue(
                response.bodyAsText().contains("\"days\":${RemoteControlServer.DEFAULT_LOG_DAYS}"),
                "query=$query",
            )
        }
    }

    @Test
    fun `logs on empty store returns empty list`() = testApplication {
        application {
            remoteControlModule(FakeStreamControl(), TOKEN, newLogStore("logs_empty"))
        }
        val response = client.get(PATH_LOGS) {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"count\":0"))
        assertTrue(body.contains("\"entries\":[]"))
    }

    @Test
    fun `unknown route returns not found`() = testApplication {
        application {
            remoteControlModule(FakeStreamControl(), TOKEN, newLogStore())
        }
        val response = client.get("/unknown")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
