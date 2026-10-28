package com.vivid.feature.streaming.compositor.audio

import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.audio.SilenceAudioSource

internal data class SceneAudioInput(val key: Any, val create: () -> AudioSource)
internal enum class SceneAudioInputKind { DISABLED, MICROPHONE }

/**
 * A stable RootEncoder input keeps the prepared audio format and encoder across
 * live source changes. Control calls serialize separately from PCM delivery:
 * stopping a decoder never waits for its callback while holding the callback lock.
 */
internal class SceneAudioRouterSource(
    private val fallback: () -> AudioSource = ::SilenceAudioSource,
) : AudioSource() {
    private val control = Any()
    private val delivery = Any()
    private data class Profile(val rate: Int, val stereo: Boolean, val echo: Boolean, val noise: Boolean)
    private var profile: Profile? = null
    private var desired = disabled()
    private var delegate: AudioSource? = null
    private var generation = 0L
    private var running = false
    private var closed = false
    private var recoveryNeeded = false
    val requestedKey: Any get() = synchronized(control) { desired.key }

    override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
        synchronized(control) {
            check(!closed && !isRunning())
            require(sampleRate > 0)
            profile = Profile(sampleRate, isStereo, echoCanceler, noiseSuppressor)
        }
        return true
    }

    /** Preparing a new input can fail without stopping the input currently on air. */
    fun select(input: SceneAudioInput, force: Boolean = false) {
        synchronized(control) {
            check(!closed)
            if (input.key == desired.key && !force && !recoveryNeeded) return
            if (!isRunning()) { desired = input; recoveryNeeded = false; return }
            val candidate = prepare(input.create) // The previous delegate is still running.
            val previous = desired
            val old = detach()
            try {
                old?.let(::dispose)
                activate(candidate)
                desired = input
                recoveryNeeded = false
            } catch (error: Throwable) {
                cleanup(detach() ?: candidate, error)
                recoverSwitch(previous, error)
                throw error
            }
        }
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        synchronized(control) {
            check(created && !closed)
            synchronized(delivery) {
                this.getMicrophoneData = getMicrophoneData
                if (running) return
                running = true
            }
            var candidate: AudioSource? = null
            try {
                candidate = prepare(desired.create)
                activate(candidate)
                recoveryNeeded = false
            } catch (error: Throwable) {
                val failed = detach()
                (failed ?: candidate)?.let { cleanup(it, error) }
                synchronized(delivery) { running = false; this.getMicrophoneData = null }
                throw error
            }
        }
    }

    private fun prepare(factory: () -> AudioSource): AudioSource {
        val config = requireNotNull(profile)
        val candidate = factory()
        try {
            check(candidate.init(config.rate, config.stereo, config.echo, config.noise)) { "Could not prepare scene audio input" }
            return candidate
        } catch (error: Throwable) { cleanup(candidate, error); throw error }
    }

    private fun activate(candidate: AudioSource) {
        val token = synchronized(delivery) { delegate = candidate; ++generation }
        candidate.start(object : GetMicrophoneData {
            override fun inputPCMData(frame: Frame) {
                synchronized(delivery) {
                    if (running && generation == token && delegate === candidate)
                        getMicrophoneData?.inputPCMData(frame)
                }
            }
        })
    }

    private fun detach(): AudioSource? = synchronized(delivery) {
        generation++
        delegate.also { delegate = null }
    }

    private fun recoverSwitch(previous: SceneAudioInput, error: Throwable) {
        if (error is Exception) recover(previous, error)
        else synchronized(delivery) { running = false; getMicrophoneData = null }
    }

    private fun recover(previous: SceneAudioInput, error: Exception) {
        var candidate: AudioSource? = null
        try {
            candidate = prepare(previous.create)
            activate(candidate)
            recoveryNeeded = false
        } catch (restore: Exception) {
            error.addSuppressed(restore)
            (detach() ?: candidate)?.let { cleanup(it, error) }
            candidate = null
            recoveryNeeded = true
            try {
                candidate = prepare(fallback)
                activate(candidate)
            } catch (silent: Exception) {
                error.addSuppressed(silent)
                (detach() ?: candidate)?.let { cleanup(it, error) }
                synchronized(delivery) { running = false; getMicrophoneData = null }
            }
        }
    }

    private fun cleanup(source: AudioSource, error: Throwable) {
        try { dispose(source) } catch (close: Throwable) { error.addSuppressed(close) }
    }

    private fun dispose(source: AudioSource) {
        var error: Throwable? = null
        try { source.stop() } catch (failure: Throwable) { error = failure }
        try { source.release() } catch (failure: Throwable) {
            if (error == null) error = failure else error.addSuppressed(failure)
        }
        error?.let { throw it }
    }

    fun seekFileAudio(positionSeconds: Double) {
        synchronized(control) { (delegate as? SceneFileAudioSource)?.seekTo(positionSeconds) }
    }

    override fun isRunning(): Boolean = synchronized(delivery) { running }

    override fun stop() {
        synchronized(control) {
            val old = synchronized(delivery) {
                running = false
                getMicrophoneData = null
                generation++
                delegate.also { delegate = null }
            }
            old?.let(::dispose)
        }
    }

    override fun release() {
        synchronized(control) {
            if (closed) return
            closed = true
            try { stop() }
            finally { created = false; profile = null; desired = disabled() }
        }
    }

    companion object {
        fun disabled() = SceneAudioInput(SceneAudioInputKind.DISABLED, ::NoAudioSource)
    }
}
