package com.vivid.irlbroadcaster

import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vivid.feature.streaming.R as StreamingR
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Socket

/** Opt-in device regression: requires an explicitly configured loopback RTMP receiver.
 * Never starts a stream to a saved external endpoint. Semantics invoke the real
 * button handler even on HyperOS devices that block shell input injection.
 */
@RunWith(AndroidJUnit4::class)
class StreamRestartInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun buttonStartStopStartKeepsUiAndRemoteStatusInSync() {
        val settings = runBlocking { composeRule.activity.settingsRepository.appSettingsFlow.first() }
        assumeTrue("Requires the local USB test receiver", settings.streamUrl == "rtmp://127.0.0.1:1936/live" && settings.streamKey == "probe")
        repeat(2) { cycle ->
            Log.i("VividRestartTest", "button cycle=${cycle + 1} starting")
            click(StreamingR.string.streaming_start)
            awaitStatus("STREAMING")
            Log.i("VividRestartTest", "button cycle=${cycle + 1} streaming")
            Thread.sleep(1_500) // Let real encoded video reach the USB receiver before stopping.
            click(StreamingR.string.streaming_stop)
            awaitStatus("IDLE")
            Log.i("VividRestartTest", "button cycle=${cycle + 1} stopped")
        }
    }

    @Test
    fun buttonRetriesAfterConnectionFailureWithoutRestartingActivity() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runFailureRecovery") == "true")
        val settings = runBlocking { composeRule.activity.settingsRepository.appSettingsFlow.first() }
        assumeTrue(settings.streamUrl == "rtmp://127.0.0.1:1936/live" && settings.streamKey == "probe")
        click(StreamingR.string.streaming_start)
        awaitStatus("FAILED")
        Log.i("VividRestartTest", "button failed; waiting for local receiver restoration")
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runCatching { localHttp(19987, "/v3/paths/list").contains("\"items\"") }.getOrDefault(false)
        }
        click(StreamingR.string.streaming_retry)
        awaitStatus("STREAMING")
        Thread.sleep(1_500)
        Log.i("VividRestartTest", "button recovery streaming after failure")
        click(StreamingR.string.streaming_stop)
        awaitStatus("IDLE")
    }

    private fun localHttp(port: Int, path: String): String = Socket().use { socket ->
        socket.soTimeout = 500
        socket.connect(InetSocketAddress("127.0.0.1", port), 500)
        socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray())
        socket.getInputStream().bufferedReader().readText()
    }

    private fun click(resId: Int) {
        val label = composeRule.activity.getString(resId)
        composeRule.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick) { it.invoke() }
    }

    private fun awaitStatus(expected: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                localHttp(8080, "/status").contains("\"$expected\"")
            }.getOrDefault(false)
        }
    }
}
