package com.vivid.feature.streaming.whip

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WHIPSmokeRunnerTest {

    private val appContext: Context = mockk(relaxed = true)

    @Test
    fun `run reports success with peerconnectionfactory detail when smoke returns true`() {
        val result = WHIPSmokeRunner.run(appContext) { true }

        assertTrue(result.ok)
        assertTrue(result.detail.contains("PeerConnectionFactory"))
        assertTrue(result.detail.contains("dlopen"))
    }

    @Test
    fun `run reports failure with logcat hint when smoke returns false`() {
        val result = WHIPSmokeRunner.run(appContext) { false }

        assertFalse(result.ok)
        assertTrue(result.detail.contains("sdkSmoke"))
        assertTrue(result.detail.contains("WHIPIngestProbe"))
    }

    @Test
    fun `run maps thrown UnsatisfiedLinkError to failure detail with class name and message`() {
        val result = WHIPSmokeRunner.run(appContext) {
            throw UnsatisfiedLinkError("dlopen failed: library \"libjingle_pcm4d.so\" not found")
        }

        assertFalse(result.ok)
        assertEquals("java.lang.UnsatisfiedLinkError", result.detail.substringBefore(":"))
        assertTrue(result.detail.contains("dlopen failed"))
        assertTrue(result.detail.contains("libjingle_pcm4d.so"))
    }

    @Test
    fun `run maps thrown NoClassDefFoundError with r8 hint and handles missing message`() {
        val result = WHIPSmokeRunner.run(appContext) {
            throw NoClassDefFoundError("org/webrtc/PeerConnectionFactory")
        }

        assertFalse(result.ok)
        assertTrue(result.detail.contains("java.lang.NoClassDefFoundError"))
        assertTrue(result.detail.contains("org/webrtc/PeerConnectionFactory"))
        assertTrue(result.detail.contains("proguard-rules.pro"))
    }

    @Test
    fun `run keeps neutral placeholder when throwable carries no message`() {
        val result = WHIPSmokeRunner.run(appContext) {
            throw RuntimeException()
        }

        assertFalse(result.ok)
        assertTrue(result.detail.contains("java.lang.RuntimeException"))
        assertTrue(result.detail.contains("(keine Message)"))
    }
}
