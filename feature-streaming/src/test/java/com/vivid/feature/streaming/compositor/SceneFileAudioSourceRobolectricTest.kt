package com.vivid.feature.streaming.compositor

import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioSource
import com.vivid.feature.streaming.compositor.audio.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SceneFileAudioSourceRobolectricTest {
    private class Driver(val events: SceneFileAudioDriver.Listener) : SceneFileAudioDriver {
        var format: SceneFileAudioFormat? = SceneFileAudioFormat(32_000, 1)
        var opens = 0
        var closes = 0
        var position: Double? = null
        var stopped = false
        var openFailure: Exception? = null
        override fun open(): SceneFileAudioFormat? { opens++; openFailure?.let { throw it }; return format }
        override fun start(positionSeconds: Double) { position = positionSeconds }
        override fun seek(positionSeconds: Double, discard: () -> Unit) { discard(); position = positionSeconds }
        override fun close() { if (!stopped) { stopped = true; closes++ } }
    }
    private class Silence : AudioSource() {
        var starts = 0
        var stops = 0
        var releases = 0
        var onStop: (() -> Unit)? = null
        var startFailure: Exception? = null
        var receiver: GetMicrophoneData? = null
        override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean) = true
        override fun start(getMicrophoneData: GetMicrophoneData) {
            starts++; receiver = getMicrophoneData; startFailure?.let { throw it }
        }
        override fun stop() { stops++; onStop?.invoke() }
        override fun isRunning() = starts > stops
        override fun release() { releases++ }
    }
    private val drivers = mutableListOf<Driver>()
    private val silences = mutableListOf<Silence>()
    private val frames = mutableListOf<Frame>()
    private val errors = mutableListOf<Exception>()
    private val receiver = object : GetMicrophoneData {
        override fun inputPCMData(frame: Frame) { frames.add(frame) }
    }
    private var configureDriver: (Driver) -> Unit = {}
    private var configureSilence: (Silence) -> Unit = {}
    private var position = 0.0
    private val source = SceneFileAudioSource({ events ->
        Driver(events).also { configureDriver(it); drivers.add(it) }
    }, { position }, errors::add, {
        Silence().also { configureSilence(it); silences.add(it) }
    }, { it.close() })
    private fun start() {
        assertTrue(source.init(32_000, true, false, false))
        source.start(receiver)
    }
    private fun pcm(vararg values: Int) = ByteBuffer.allocate(values.size * 2).order(ByteOrder.nativeOrder())
        .apply { values.forEach { putShort(it.toShort()) } }.array()
    private fun emit(driver: Driver, value: Int, time: Long = 1000) =
        driver.events.pcm(SceneFileAudioFormat(32_000, 1), pcm(value), time)

    @Test fun `file audio joins the preview position and forwards owned stereo PCM`() {
        position = 12.5; start()
        val driver = drivers.single()
        assertEquals(12.5, driver.position!!, 0.0)
        val packet = pcm(1200)
        driver.events.pcm(SceneFileAudioFormat(32_000, 1), packet, 1000)
        packet.fill(0)
        assertArrayEquals(pcm(1200, 1200), frames.single().buffer)
        assertEquals(4, frames.single().size)
        assertTrue(silences.isEmpty())
        source.stop(); source.release()
        assertEquals(1, driver.closes)
    }
    @Test fun `a file without an audio track produces silence without a decoder or microphone`() {
        configureDriver = { it.format = null }
        start()
        assertEquals(1, drivers.single().closes)
        assertEquals(1, silences.single().starts)
        assertEquals(32_000, silences.single().sampleRate)
        assertTrue(silences.single().isStereo)
        source.release()
        assertEquals(1, silences.single().stops)
        assertEquals(1, silences.single().releases)
    }
    @Test fun `end of file keeps output alive with silence and ignores late decoder packets`() {
        start(); val driver = drivers.single()
        emit(driver, 100)
        driver.events.ended()
        emit(driver, 999)
        driver.events.ended()
        assertEquals(1, frames.size)
        assertEquals(1, driver.closes)
        assertEquals(1, silences.single().starts)
        assertTrue(source.isRunning())
        val silentFrame = Frame(pcm(0, 0), 0, 4, 2000)
        silences.single().receiver!!.inputPCMData(silentFrame)
        assertSame(silentFrame, frames.last())
        source.stop()
        silences.single().receiver!!.inputPCMData(silentFrame)
        assertEquals(2, frames.size)
        source.release()
    }
    @Test fun `restart obtains a new decoder and stale callbacks cannot affect it`() {
        start(); val old = drivers.single()
        source.stop()
        source.start(receiver)
        val current = drivers.last()
        old.events.failed(IllegalStateException("late")); old.events.ended(); emit(old, 999)
        emit(current, 42)
        assertTrue(errors.isEmpty()); assertTrue(silences.isEmpty())
        assertArrayEquals(pcm(42, 42), frames.single().buffer)
        source.release(); source.release()
        assertEquals(listOf(1, 1), drivers.map { it.closes })
    }
    @Test fun `seek discards a partial old packet before new audio arrives`() {
        start(); val driver = drivers.single()
        driver.events.pcm(SceneFileAudioFormat(32_000, 1), byteArrayOf(1), 1000)
        source.seekTo(4.0)
        emit(driver, 64, 2000)
        assertEquals(4.0, driver.position!!, 0.0)
        assertArrayEquals(pcm(64, 64), frames.single().buffer)
        assertEquals(2000L, frames.single().timeStamp)
        source.release()
    }
    @Test fun `seek after EOF reopens audio at the requested media position without reviving old silence`() {
        start(); val old = drivers.single()
        old.events.ended()
        val previousSilence = silences.single()
        source.seekTo(7.0)
        val current = drivers.last()
        assertEquals(2, drivers.size)
        assertEquals(7.0, current.position!!, 0.0)
        assertEquals(1, previousSilence.stops); assertEquals(1, previousSilence.releases)
        previousSilence.receiver!!.inputPCMData(Frame(pcm(999, 999), 0, 4, 1000))
        emit(old, 999); emit(current, 64, 2000)
        assertArrayEquals(pcm(64, 64), frames.single().buffer)
        source.release()
    }
    @Test fun `decoder failure switches to silence exactly once and retains its cause`() {
        start(); val driver = drivers.single()
        val error = IllegalStateException("codec failed")
        driver.events.failed(error); driver.events.failed(error); emit(driver, 999)
        assertEquals(listOf(error), errors)
        assertEquals(1, driver.closes)
        assertEquals(1, silences.single().starts)
        assertTrue(frames.isEmpty())
        source.release()
    }
    @Test fun `failed silence startup still closes decoder and stops delivery`() {
        configureSilence = { it.startFailure = IllegalStateException("silence failed") }
        start(); val driver = drivers.single()
        driver.events.ended()
        assertFalse(source.isRunning())
        assertEquals(1, driver.closes)
        assertEquals(1, silences.single().releases)
        assertEquals("silence failed", errors.single().message)
        emit(driver, 999); assertTrue(frames.isEmpty())
        source.release()
    }
    @Test fun `failed silence cleanup can join a late callback without holding the delivery lock`() {
        configureSilence = { silence ->
            silence.startFailure = IllegalStateException("silence failed")
            silence.onStop = {
                val callback = Thread {
                    silence.receiver!!.inputPCMData(Frame(pcm(123, 123), 0, 4, 1000))
                }.apply { isDaemon = true; start() }
                callback.join(2000)
                assertFalse("Cleanup held the PCM delivery lock", callback.isAlive)
            }
        }
        start()
        drivers.single().events.ended()
        assertTrue(frames.isEmpty())
        assertFalse(source.isRunning())
        assertEquals(1, silences.single().stops)
        assertEquals(1, silences.single().releases)
        source.release()
    }
    @Test fun `failed initialization closes its extractor and cannot start an unprepared source`() {
        configureDriver = { it.openFailure = IllegalArgumentException("bad track") }
        assertThrows(IllegalArgumentException::class.java) { source.init(32_000, true, false, false) }
        assertEquals(1, drivers.single().closes)
        assertThrows(IllegalStateException::class.java) { source.start(receiver) }
        source.release()
    }
    @Test fun `decoded packets exclude stale tail bytes and preserve original buffer bounds`() {
        val buffer = ByteBuffer.wrap(byteArrayOf(99, 1, 2, 3, 4, 98, 97))
        (buffer as java.nio.Buffer).position(1)
        (buffer as java.nio.Buffer).limit(5)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), sceneDecodedAudioBytes(buffer, 1, 4))
        assertEquals(1, buffer.position()); assertEquals(5, buffer.limit())
        assertArrayEquals(byteArrayOf(3, 4), sceneDecodedAudioBytes(buffer, 3, 2))
        assertThrows(IllegalArgumentException::class.java) { sceneDecodedAudioBytes(buffer, 5, 3) }
    }
}
