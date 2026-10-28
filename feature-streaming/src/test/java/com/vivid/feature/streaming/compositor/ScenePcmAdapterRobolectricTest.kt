package com.vivid.feature.streaming.compositor

import com.vivid.feature.streaming.compositor.audio.ScenePcmAdapter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin
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
class ScenePcmAdapterRobolectricTest {
    private fun pcm(vararg samples: Int): ByteArray = ByteBuffer.allocate(samples.size * 2)
        .order(ByteOrder.nativeOrder()).apply { samples.forEach { putShort(it.toShort()) } }.array()
    private fun samples(bytes: ByteArray): List<Int> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        return List(bytes.size / 2) { buffer.short.toInt() }
    }
    private fun convert(bytes: ByteArray, rate: Int, channels: Int, parts: IntArray): ByteArray {
        val result = ByteArrayOutputStream()
        ScenePcmAdapter(rate, channels, 32_000, 2) { data, _ -> result.write(data) }.use { adapter ->
            var offset = 0
            var part = 0
            while (offset < bytes.size) {
                val length = minOf(parts[part++ % parts.size], bytes.size - offset)
                adapter.queue(bytes, offset, length, 1_000_000L + offset)
                offset += length
            }
            adapter.finish()
        }
        return result.toByteArray()
    }

    @Test fun `44100 Hz mono becomes one second of 32000 Hz stereo without changing DC level`() {
        val input = pcm(*IntArray(44_100) { 12_000 })
        val output = samples(convert(input, 44_100, 1, intArrayOf(input.size)))
        assertTrue(abs(output.size / 2 - 32_000) <= 1)
        assertTrue(output.chunked(2).all { it[0] == it[1] })
        assertTrue(output.drop(128).dropLast(128).all { abs(it - 12_000) <= 1 })
    }
    @Test fun `resampling is independent of packet boundaries including split stereo frames`() {
        val input = pcm(*IntArray(9600) { (sin(it / 2 * 0.07) * 10_000).toInt() })
        assertArrayEquals(convert(input, 48_000, 2, intArrayOf(input.size)),
            convert(input, 48_000, 2, intArrayOf(1, 7, 31, 509, 5, 260)))
    }
    @Test fun `timestamps follow emitted samples and output owns its arrays`() {
        val frames = mutableListOf<Pair<ByteArray, Long>>()
        ScenePcmAdapter(8_000, 1, 8_000, 2) { data, time -> frames.add(data to time) }.use { adapter ->
            val input = pcm(9999, 100, 200, -300, 400, 500)
            adapter.queue(input, 2, 6, 1_000_000)
            adapter.queue(input, 8, 4, 9_000_000) // Scheduling jitter must not create an audio gap.
            input.fill(0)
            adapter.finish()
        }
        assertEquals(listOf(1_000_000L, 1_000_375L), frames.map { it.second })
        assertEquals(listOf(100, 100, 200, 200, -300, -300), samples(frames[0].first))
        assertEquals(listOf(400, 400, 500, 500), samples(frames[1].first))
    }
    @Test fun `large converted buffers are losslessly packetized for the AAC input buffer`() {
        val frames = mutableListOf<Pair<ByteArray, Long>>()
        val input = pcm(*IntArray(8000) { 1234 })
        ScenePcmAdapter(8_000, 1, 32_000, 2) { bytes, time ->
            frames.add(bytes to time)
        }.use { adapter ->
            adapter.queue(input, 0, input.size, 123_000L)
            adapter.finish()
        }
        assertTrue(frames.size > 1)
        var sampleFrames = 0L
        frames.forEach { (bytes, time) ->
            assertTrue(bytes.isNotEmpty() && bytes.size <= com.pedro.encoder.audio.AudioEncoder.inputSize)
            assertEquals(0, bytes.size % 4)
            assertEquals(123_000L + sampleFrames * 1_000_000L / 32_000L, time)
            sampleFrames += bytes.size / 4
        }
        assertTrue(abs(sampleFrames - 32_000L) <= 1L)
        assertTrue(frames.map { it.first }.distinctBy { System.identityHashCode(it) }.size == frames.size)
        assertTrue(frames.flatMap { samples(it.first) }.drop(128).dropLast(128).all { abs(it - 1234) <= 1 })
    }
    @Test fun `stereo downmix does not overflow at full scale`() {
        val result = ByteArrayOutputStream()
        ScenePcmAdapter(8_000, 2, 8_000, 1) { data, _ -> result.write(data) }.use { adapter ->
            val input = pcm(32767, 32767, -32768, -32768, 1000, -1000)
            adapter.queue(input, 0, input.size, 10)
            adapter.finish()
        }
        assertEquals(listOf(32767, -32768, 0), samples(result.toByteArray()))
    }
    @Test fun `seek reset discards buffered audio and starts a new sample timeline`() {
        val frames = mutableListOf<Pair<ByteArray, Long>>()
        ScenePcmAdapter(48_000, 1, 32_000, 2) { data, time -> frames.add(data to time) }.use { adapter ->
            val old = pcm(*IntArray(120) { 8000 })
            adapter.queue(old, 0, old.size, 1000)
            adapter.reset()
            frames.clear()
            val silence = pcm(*IntArray(4800))
            adapter.queue(silence, 0, silence.size, 2000)
            adapter.finish()
            adapter.finish()
        }
        assertEquals(2000L, frames.first().second)
        assertTrue(frames.flatMap { samples(it.first) }.all { it == 0 })
    }
    @Test fun `truncated final PCM frame is rejected and stopped adapters cannot deliver more audio`() {
        var delivered = 0
        val adapter = ScenePcmAdapter(8_000, 2, 8_000, 2) { _, _ -> delivered++ }
        adapter.queue(byteArrayOf(1), 0, 1, 0)
        assertThrows(IllegalStateException::class.java) { adapter.finish() }
        adapter.reset()
        adapter.finish()
        adapter.close()
        adapter.close()
        assertThrows(IllegalStateException::class.java) { adapter.queue(pcm(1, 2), 0, 4, 1) }
        assertEquals(0, delivered)
    }
}
