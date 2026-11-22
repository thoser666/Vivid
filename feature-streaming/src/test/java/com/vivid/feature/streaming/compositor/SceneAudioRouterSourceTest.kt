package com.vivid.feature.streaming.compositor

import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioSource
import com.vivid.feature.streaming.compositor.audio.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneAudioRouterSourceTest {
    private class Input : AudioSource() {
        var initOk = true
        var startError: Exception? = null
        var stopError: Throwable? = null
        var releaseError: Throwable? = null
        var starts = 0
        var stops = 0
        var releases = 0
        var receiver: GetMicrophoneData? = null
        var afterStop: (() -> Unit)? = null
        override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean) = initOk
        override fun start(getMicrophoneData: GetMicrophoneData) { starts++; receiver = getMicrophoneData; startError?.let { throw it } }
        override fun stop() { stops++; afterStop?.invoke(); stopError?.let { throw it } }
        override fun isRunning() = starts > stops
        override fun release() { releases++; releaseError?.let { throw it } }
        fun emit(value: Byte = 1) = Frame(byteArrayOf(value, 0), 0, 2, 1000).also { receiver?.inputPCMData(it) }
    }
    private val frames = mutableListOf<Frame>()
    private val receiver = object : GetMicrophoneData { override fun inputPCMData(frame: Frame) { frames.add(frame) } }
    private val fallbacks = mutableListOf<Input>()
    private val router = SceneAudioRouterSource { Input().also(fallbacks::add) }.also {
        assertTrue(it.init(32_000, true, true, false))
    }
    private fun input(key: String, made: MutableList<Input>, configure: (Input, Int) -> Unit = { _, _ -> }) =
        SceneAudioInput(key) { Input().also { configure(it, made.size); made.add(it) } }

    @Test fun `idle selection opens no input and repeated start does not create another capture`() {
        val made = mutableListOf<Input>(); val selected = input("file", made)
        router.select(selected); assertTrue(made.isEmpty())
        router.start(receiver); router.start(receiver)
        assertEquals(1, made.size); assertEquals(1, made.single().starts)
        assertEquals(32_000, made.single().sampleRate); assertTrue(made.single().isStereo)
        assertTrue(made.single().echoCanceler); assertFalse(made.single().noiseSuppressor)
        assertSame(made.single().emit(), frames.single())
        router.release(); router.release()
        assertEquals(1, made.single().stops); assertEquals(1, made.single().releases)
    }
    @Test fun `failed input preparation preserves the current source and its packets`() {
        val old = mutableListOf<Input>(); val bad = mutableListOf<Input>()
        router.select(input("old", old)); router.start(receiver)
        assertThrows(IllegalStateException::class.java) { router.select(input("bad", bad) { source, _ -> source.initOk = false }) }
        assertEquals("old", router.requestedKey)
        assertEquals(0, old.single().stops)
        assertSame(old.single().emit(), frames.single())
        assertEquals(1, bad.single().releases); assertTrue(fallbacks.isEmpty())
        router.release()
    }
    @Test fun `failed start restores the previous input with a fresh decoder and rejects stale callbacks`() {
        val old = mutableListOf<Input>(); val bad = mutableListOf<Input>()
        router.select(input("old", old)); router.start(receiver)
        assertThrows(IllegalArgumentException::class.java) {
            router.select(input("bad", bad) { source, _ -> source.startError = IllegalArgumentException("codec failed") })
        }
        assertEquals(2, old.size); assertEquals("old", router.requestedKey)
        old.first().emit(); bad.single().emit()
        assertTrue(frames.isEmpty())
        assertSame(old.last().emit(), frames.single())
        assertEquals(1, old.first().releases); assertEquals(1, bad.single().releases)
        router.release()
    }
    @Test fun `failed rollback uses silence and the same requested key can retry`() {
        val old = mutableListOf<Input>(); val bad = mutableListOf<Input>()
        val selected = input("file", old) { source, index -> if (index == 1) source.initOk = false }
        router.select(selected); router.start(receiver)
        val error = assertThrows(IllegalArgumentException::class.java) {
            router.select(input("bad", bad) { source, _ -> source.startError = IllegalArgumentException("bad file") })
        }
        assertTrue(error.suppressed.isNotEmpty()); assertTrue(router.isRunning())
        assertEquals("file", router.requestedKey); assertEquals(1, fallbacks.size)
        assertSame(fallbacks.single().emit(0), frames.single())
        router.select(selected)
        assertEquals(3, old.size); assertEquals(1, fallbacks.single().releases)
        router.release()
    }
    @Test fun `stop and restart retain the desired file but never accept packets from its old lease`() {
        val made = mutableListOf<Input>()
        router.select(input("file", made)); router.start(receiver); router.stop(); router.start(receiver)
        made.first().emit(); assertTrue(frames.isEmpty())
        assertSame(made.last().emit(), frames.single())
        router.release(); made.last().emit(); assertEquals(1, frames.size)
        assertThrows(IllegalStateException::class.java) { router.start(receiver) }
    }
    @Test fun `stop and release errors still retire the callback and release the input`() {
        val made = mutableListOf<Input>()
        router.select(input("file", made) { source, _ ->
            source.stopError = IllegalStateException("stop failed")
            source.releaseError = IllegalArgumentException("release failed")
        }); router.start(receiver)
        val error = assertThrows(IllegalStateException::class.java) { router.stop() }
        assertEquals("release failed", error.suppressed.single().message)
        assertFalse(router.isRunning()); made.single().emit(); assertTrue(frames.isEmpty())
        assertEquals(1, made.single().releases); router.release()
    }
    @Test fun `fatal stop errors still release the input and retire its callback`() {
        val made = mutableListOf<Input>()
        router.select(input("file", made) { source, _ -> source.stopError = LinkageError("driver unavailable") })
        router.start(receiver)
        assertThrows(LinkageError::class.java) { router.stop() }
        assertEquals(1, made.single().releases)
        assertFalse(router.isRunning()); made.single().emit(); assertTrue(frames.isEmpty())
        router.release()
    }
    @Test fun `decoder stop can wait for its last callback without holding the delivery lock`() {
        val made = mutableListOf<Input>()
        router.select(input("file", made)); router.start(receiver)
        made.single().afterStop = {
            val callback = Thread { made.single().emit() }.apply { isDaemon = true; start() }
            callback.join(1000)
            assertFalse(callback.isAlive, "PCM callback was blocked by decoder shutdown")
        }
        router.stop(); assertTrue(frames.isEmpty()); router.release()
    }
    @Test fun `illegal live reinitialization cannot change the next input's encoder profile`() {
        val first = mutableListOf<Input>(); val second = mutableListOf<Input>()
        router.select(input("first", first)); router.start(receiver)
        assertThrows(IllegalStateException::class.java) { router.init(48_000, false, false, true) }
        router.select(input("second", second))
        assertEquals(32_000, second.single().sampleRate); assertTrue(second.single().isStereo)
        assertTrue(second.single().echoCanceler); assertFalse(second.single().noiseSuppressor)
        router.release()
    }
}
