package com.vivid.feature.streaming.compositor.audio

import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.encoder.input.sources.audio.SilenceAudioSource
import java.io.Closeable
import java.util.concurrent.Executors
import timber.log.Timber

internal data class SceneFileAudioFormat(val rate: Int, val channels: Int)

/** Driver callbacks consume each packet synchronously before its codec buffer is reused. */
internal interface SceneFileAudioDriver : Closeable {
    interface Listener {
        fun pcm(format: SceneFileAudioFormat, bytes: ByteArray, timeUs: Long)
        fun ended()
        fun failed(error: Exception)
    }
    fun open(): SceneFileAudioFormat? // null means a readable file without an audio track.
    fun start(positionSeconds: Double)
    /** Calls discard under the same decoder lock as PCM delivery. */
    fun seek(positionSeconds: Double, discard: () -> Unit)
}

/**
 * Adapts a file decoder to RootEncoder's existing audio encoder. Consumer lifetime
 * controls decoding; end-of-file/errors produce silence rather than opening a mic.
 */
internal class SceneFileAudioSource(
    private val driverFactory: (SceneFileAudioDriver.Listener) -> SceneFileAudioDriver,
    private val positionSeconds: () -> Double = { 0.0 },
    private val onFailure: (Exception) -> Unit = { Timber.w(it, "Could not decode scene file audio") },
    private val silenceFactory: () -> AudioSource = ::SilenceAudioSource,
    private val retire: (Closeable) -> Unit = { resource ->
        cleanup.execute { runCatching { resource.close() }.onFailure { Timber.w(it, "Could not close scene file audio") } }
    },
) : AudioSource() {
    private val lock = Any()
    private var generation = 0L
    private var driver: SceneFileAudioDriver? = null
    private var silence: AudioSource? = null
    private var format: SceneFileAudioFormat? = null
    private var adapter: ScenePcmAdapter? = null
    private var ended = false
    private var running = false
    private var released = false

    override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
        synchronized(lock) {
            check(!released && !running)
            created = false
            closePrepared()
            open()
        }
        return true
    }

    private fun open() {
        val token = ++generation
        val candidate = driverFactory(object : SceneFileAudioDriver.Listener {
            override fun pcm(format: SceneFileAudioFormat, bytes: ByteArray, timeUs: Long) {
                try {
                    synchronized(lock) {
                        if (!running || generation != token || driver == null) return
                        if (this@SceneFileAudioSource.format != format) configureAdapter(format)
                        requireNotNull(adapter).queue(bytes, 0, bytes.size, timeUs)
                    }
                } catch (error: Exception) { failed(error) }
            }
            override fun ended() = finish(token, null)
            override fun failed(error: Exception) = finish(token, error)
        })
        try {
            val input = candidate.open()
            if (input == null) {
                candidate.close()
                silence = newSilence()
            } else {
                configureAdapter(input)
                driver = candidate
            }
        } catch (error: Exception) {
            runCatching { candidate.close() }
            adapter?.close(); adapter = null; format = null
            throw error
        }
    }

    private fun configureAdapter(input: SceneFileAudioFormat) {
        adapter?.close()
        format = input
        adapter = ScenePcmAdapter(input.rate, input.channels, sampleRate, if (isStereo) 2 else 1) { bytes, time ->
            // Called under lock, so stop cannot return while a packet is being delivered.
            if (running) getMicrophoneData?.inputPCMData(Frame(bytes, 0, bytes.size, time))
        }
    }

    override fun start(getMicrophoneData: GetMicrophoneData) = startAt(positionSeconds(), getMicrophoneData)

    private fun startAt(position: Double, getMicrophoneData: GetMicrophoneData) {
        require(position.isFinite() && position >= 0)
        val active: Triple<SceneFileAudioDriver?, AudioSource?, Long>
        synchronized(lock) {
            check(created && !released && !running)
            if (driver == null && silence == null) open()
            this.getMicrophoneData = getMicrophoneData
            ended = false
            running = true
            active = Triple(driver, silence, generation)
        }
        try {
            active.first?.start(position)
            active.second?.let { it.start(guardedSilence(it)) }
        } catch (error: Exception) {
            finish(active.third, error)
        }
    }

    fun seekTo(positionSeconds: Double) {
        require(positionSeconds.isFinite() && positionSeconds >= 0)
        val restart = synchronized(lock) { if (running && ended) getMicrophoneData else null }
        if (restart != null) {
            stop()
            startAt(positionSeconds, restart)
            return
        }
        val (active, token) = synchronized(lock) { (if (running) driver else null) to generation }
        if (active == null) return
        try {
            active.seek(positionSeconds) {
                synchronized(lock) { if (running && driver === active) adapter?.reset() }
            }
        } catch (error: Exception) { finish(token, error) }
    }

    private fun finish(token: Long, error: Exception?) {
        var retired: SceneFileAudioDriver? = null
        var failedSilence: AudioSource? = null
        var failure = error
        synchronized(lock) {
            if (!running || token != generation || driver == null) return
            if (failure == null) {
                try { adapter?.finish() } catch (cause: Exception) { failure = cause }
            }
            ended = failure == null
            retired = driver; driver = null
            generation++
            adapter?.close(); adapter = null; format = null
            try {
                val next = newSilence().also { silence = it }
                next.start(guardedSilence(next))
            } catch (cause: Exception) {
                running = false
                ended = false
                getMicrophoneData = null
                failedSilence = silence; silence = null
                if (failure == null) failure = cause else failure.addSuppressed(cause)
            }
        }
        // stop may join a callback waiting for lock, so cleanup belongs outside it.
        failedSilence?.let { source ->
            runCatching { source.stop() }.exceptionOrNull()?.let { failure?.addSuppressed(it) }
            runCatching { source.release() }.exceptionOrNull()?.let { failure?.addSuppressed(it) }
        }
        // A callback may finish on the decoder thread; closing its codec happens elsewhere.
        retired?.let { resource ->
            try { retire(resource) } catch (cause: Exception) {
                runCatching { resource.close() }.exceptionOrNull()?.let(cause::addSuppressed)
                if (failure == null) failure = cause else failure.addSuppressed(cause)
            }
        }
        failure?.let(onFailure)
    }

    private fun newSilence(): AudioSource {
        val candidate = silenceFactory()
        try {
            check(candidate.init(sampleRate, isStereo, false, false)) { "Could not prepare file-audio silence" }
            return candidate
        } catch (error: Exception) {
            runCatching { candidate.release() }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    private fun guardedSilence(expected: AudioSource) = object : GetMicrophoneData {
        override fun inputPCMData(frame: Frame) {
            synchronized(lock) { if (running && silence === expected) getMicrophoneData?.inputPCMData(frame) }
        }
    }

    override fun isRunning(): Boolean = synchronized(lock) { running }

    override fun stop() {
        val resources = synchronized(lock) {
            running = false
            ended = false
            generation++
            getMicrophoneData = null
            adapter?.close(); adapter = null; format = null
            (driver to silence).also { driver = null; silence = null }
        }
        try { resources.first?.close() }
        finally { resources.second?.let { try { it.stop() } finally { it.release() } } }
    }

    private fun closePrepared() {
        try { driver?.close() }
        finally {
            driver = null
            adapter?.close(); adapter = null; format = null
            silence?.release(); silence = null
        }
    }

    override fun release() {
        synchronized(lock) { if (released) return; released = true; created = false }
        stop()
    }

    companion object {
        private val cleanup by lazy {
            Executors.newSingleThreadExecutor { task -> Thread(task, "VividFileAudioCleanup").apply { isDaemon = true } }
        }
    }
}
