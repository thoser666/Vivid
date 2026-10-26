package com.vivid.feature.streaming.compositor.audio

import com.pedro.encoder.audio.AudioEncoder
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.io.Closeable
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder-worker-owned PCM16 conversion to one fixed encoder profile. Video never
 * passes through this adapter. Each delivered array is owned by the downstream
 * encoder queue; decoder and Sonic buffers may be reused immediately afterwards.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class ScenePcmAdapter(
    private val inputRate: Int,
    private val inputChannels: Int,
    private val outputRate: Int,
    private val outputChannels: Int,
    private val deliver: (bytes: ByteArray, timeUs: Long) -> Unit,
) : Closeable {
    private val processor = SonicAudioProcessor()
    private val inputFrameBytes = inputChannels * 2
    private val outputFrameBytes = outputChannels * 2
    private var carry = ByteArray(0)
    private var originUs: Long? = null
    private var outputFrames = 0L
    private var finished = false
    private var closed = false

    init {
        require(inputRate in 8_000..192_000 && outputRate in 8_000..192_000)
        require(inputChannels in 1..2 && outputChannels in 1..2)
        processor.setOutputSampleRateHz(outputRate)
        processor.configure(AudioProcessor.AudioFormat(inputRate, outputChannels, C.ENCODING_PCM_16BIT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
    }

    /** Arbitrary packet boundaries are allowed, including a split PCM sample. */
    fun queue(bytes: ByteArray, offset: Int, length: Int, timeUs: Long) {
        check(!closed && !finished)
        require(offset >= 0 && length in 0..MAX_PACKET_BYTES && offset <= bytes.size - length)
        require(timeUs >= 0)
        if (length == 0) return
        if (originUs == null) originUs = timeUs
        val input = if (carry.isEmpty()) ByteBuffer.wrap(bytes, offset, length).slice()
            else ByteBuffer.allocate(carry.size + length).apply {
                put(carry); put(bytes, offset, length); (this as Buffer).flip()
            }
        input.order(ByteOrder.nativeOrder())
        val completeBytes = input.remaining() / inputFrameBytes * inputFrameBytes
        carry = ByteArray(input.remaining() - completeBytes)
        input.duplicate().apply { (this as Buffer).position(completeBytes) }.get(carry)
        (input as Buffer).limit(completeBytes)
        if (!input.hasRemaining()) return
        val converted = if (inputChannels == outputChannels) input else mixChannels(input)
        if (processor.isActive) {
            processor.queueInput(converted)
            drain()
        } else emit(converted)
    }

    private fun mixChannels(input: ByteBuffer): ByteBuffer {
        val result = ByteBuffer.allocate(input.remaining() / inputFrameBytes * outputFrameBytes)
            .order(ByteOrder.nativeOrder())
        while (input.hasRemaining()) {
            val first = input.short
            if (inputChannels == 1) {
                result.putShort(first).putShort(first)
            } else {
                val second = input.short
                result.putShort(((first.toInt() + second.toInt()) / 2).toShort())
            }
        }
        (result as Buffer).flip()
        return result
    }

    fun finish() {
        check(!closed)
        if (finished) return
        check(carry.isEmpty()) { "Incomplete PCM frame at end of audio" }
        finished = true
        if (processor.isActive) {
            processor.queueEndOfStream()
            drain()
        }
    }

    /** Discards old samples on a seek, loop or source replacement. */
    fun reset() {
        check(!closed)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        carry = ByteArray(0)
        originUs = null
        outputFrames = 0L
        finished = false
    }

    private fun drain() {
        while (true) {
            val output = processor.output
            if (!output.hasRemaining()) return
            emit(output)
        }
    }

    private fun emit(output: ByteBuffer) {
        check(output.remaining() % outputFrameBytes == 0)
        // RootEncoder truncates frames larger than its codec input buffer. Keep
        // each owned packet within the advertised input size, on PCM boundaries.
        val packetBytes = AudioEncoder.inputSize / outputFrameBytes * outputFrameBytes
        while (output.hasRemaining()) {
            val bytes = ByteArray(minOf(output.remaining(), packetBytes)).also(output::get)
            val time = requireNotNull(originUs) + outputFrames / outputRate * 1_000_000L +
                outputFrames % outputRate * 1_000_000L / outputRate
            outputFrames += bytes.size / outputFrameBytes
            deliver(bytes, time)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        carry = ByteArray(0)
        processor.reset()
    }

    companion object { private const val MAX_PACKET_BYTES = 1_048_576 }
}
