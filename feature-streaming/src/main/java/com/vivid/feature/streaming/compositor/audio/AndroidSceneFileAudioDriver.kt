package com.vivid.feature.streaming.compositor.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.pedro.encoder.Frame
import com.pedro.encoder.CodecErrorCallback
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.decoder.AudioDecoder
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.DecoderInterface
import com.pedro.encoder.utils.CodecUtil
import com.pedro.encoder.utils.PCMUtil
import java.io.File
import java.nio.Buffer
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** Uses RootEncoder's file pacing, but forwards only the valid MediaCodec PCM region. */
internal class AndroidSceneFileAudioDriver(
    private val context: Context,
    private val uri: Uri,
    private val loop: Boolean,
    private val listener: SceneFileAudioDriver.Listener,
) : SceneFileAudioDriver {
    private val closed = AtomicBoolean(false)
    private val reportedError = AtomicBoolean(false)
    private var decoder: Decoder? = null

    override fun open(): SceneFileAudioFormat? {
        check(!closed.get() && decoder == null)
        require(uri.scheme == null || uri.scheme in listOf("content", "file")) { "File audio requires a local document" }
        val localUri = if (uri.scheme == null) Uri.fromFile(File(uri.toString())) else uri
        val extractor = MediaExtractor()
        val hasAudio = try {
            extractor.setDataSource(context, localUri, null)
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } finally { extractor.release() }
        if (!hasAudio) return null
        val candidate = Decoder()
        decoder = candidate // Own even a partially initialized extractor on failure.
        try {
            check(candidate.initExtractor(context, localUri)) { "Could not extract file audio" }
            candidate.setLoopMode(loop)
            candidate.setCodecErrorCallback(object : CodecErrorCallback {
                override fun onCodecError(type: CodecUtil.CodecTypeError, e: MediaCodec.CodecException) = fail(e)
            })
            return SceneFileAudioFormat(candidate.sampleRate, if (candidate.isStereo) 2 else 1)
        } catch (error: Exception) { close(); throw error }
    }

    override fun start(positionSeconds: Double) {
        check(!closed.get())
        require(positionSeconds.isFinite() && positionSeconds >= 0)
        val active = requireNotNull(decoder)
        active.initialPosition = positionSeconds
        check(active.prepareAudio()) { "Could not prepare file audio decoder" }
        active.start()
    }

    override fun seek(positionSeconds: Double, discard: () -> Unit) {
        check(!closed.get())
        require(positionSeconds.isFinite() && positionSeconds >= 0)
        requireNotNull(decoder).seekAndDiscard(positionSeconds, discard)
    }

    private fun fail(error: Exception) {
        if (!closed.get() && reportedError.compareAndSet(false, true)) listener.failed(error)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val active = decoder; decoder = null
        active?.stop()
    }

    private inner class Decoder : AudioDecoder(
        object : GetMicrophoneData { override fun inputPCMData(frame: Frame) = Unit },
        AudioDecoderInterface { if (!closed.get()) listener.ended() },
        object : DecoderInterface {
            override fun onLoop() { if (!closed.get()) decoder?.reset(null) }
        },
    ) {
        var initialPosition = 0.0
        private var initialSeek = true

        override fun prepareAudio(): Boolean {
            mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            return super.prepareAudio()
        }

        // BaseDecoder starts by seeking to zero. Join an already-playing preview instead.
        override fun moveTo(time: Double) {
            synchronized(sync) {
                val position = if (initialSeek) initialPosition else time
                initialSeek = false
                super.moveTo(position)
            }
        }

        fun seekAndDiscard(position: Double, discard: () -> Unit) {
            synchronized(sync) {
                discard()
                if (initialSeek) initialPosition = position else super.moveTo(position)
                if (isRunning) codec.flush()
            }
        }

        override fun decodeOutput(outputBuffer: ByteBuffer?, timeStamp: Long): Boolean {
            if (closed.get() || bufferInfo.size == 0) return false
            try {
                val outputFormat = codec.outputFormat
                val encoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                    outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                check(encoding == AudioFormat.ENCODING_PCM_16BIT) { "File decoder did not provide PCM16" }
                val rate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                require(channels > 0)
                val bytes = sceneDecodedAudioBytes(requireNotNull(outputBuffer) { "File decoder returned no PCM buffer" }, bufferInfo.offset, bufferInfo.size)
                check(bytes.size % (channels * 2) == 0) { "File decoder returned an incomplete PCM frame" }
                val pcm = if (channels > 2) PCMUtil.pcmToStereo(bytes, channels) else bytes
                listener.pcm(SceneFileAudioFormat(rate, minOf(channels, 2)), pcm, timeStamp)
            } catch (error: Exception) { fail(error) }
            return false
        }
    }
}

/** Buffer capacity can exceed its valid packet; never replay a stale tail from the previous packet. */
internal fun sceneDecodedAudioBytes(buffer: ByteBuffer, offset: Int, size: Int): ByteArray {
    require(offset >= 0 && size >= 0 && offset <= buffer.capacity() - size)
    val view = buffer.duplicate()
    (view as Buffer).limit(offset + size)
    (view as Buffer).position(offset)
    return ByteArray(size).also(view::get)
}
