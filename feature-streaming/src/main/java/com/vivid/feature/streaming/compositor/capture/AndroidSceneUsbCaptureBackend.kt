package com.vivid.feature.streaming.compositor.capture

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.compositor.SceneProducer
import com.vivid.feature.streaming.compositor.SceneProducerPermissionException
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import timber.log.Timber

/** Raw USB capture for devices without an Android external-camera HAL. Owns no encoder. */
internal class AndroidSceneUsbCaptureBackend(
    private val context: Context,
    private val devices: SceneUsbDevices = SceneUsbDevices(context),
    private val driverFactory: (UsbDevice) -> SceneUsbDriver = { AndroidSceneUsbDriver(context, it) },
    private val workerFactory: () -> ExecutorService = {
        Executors.newSingleThreadExecutor { task -> Thread(task, "VividSceneUsbCapture") }
    },
    private val watchDisconnect: ((String) -> Unit) -> Closeable = { listener -> watchSceneUsbDetach(context, listener) },
) : SceneCaptureBackend {
    private data class Demand(val frameRate: Int, val concurrent: Boolean)
    private val closed = AtomicBoolean(false)
    private val actors = ConcurrentHashMap<String, UsbProducer>()
    private val leases = ConcurrentHashMap<String, UsbProducer>()
    private var watcher: Closeable? = null
    private var concurrent = false

    override fun configure(sources: List<SceneSource>) {
        val next = sources.filter { it.type == SceneSourceType.CAMERA || it.type == SceneSourceType.UVC }
            .distinctBy { it.id }.size > 1
        if (next != concurrent) {
            concurrent = next
            actors.values.forEach { it.updateConcurrent(next) }
        }
    }
    override fun setTargetFrameRate(frameRate: Int) {
        require(frameRate in 1..120)
        actors.values.forEach { it.updateRate(frameRate) }
    }
    @Synchronized
    override fun create(source: SceneSource, target: SceneCaptureTarget, failed: (Boolean) -> Unit): SceneProducer {
        check(!closed.get())
        require(source.type == SceneSourceType.UVC)
        val locator = requireNotNull(SceneUsbLocator.parse(source.locator)) { "Unsupported USB source identity" }
        if (watcher == null) watcher = watchDisconnect { name ->
            actors.values.filter { it.deviceName == name }.forEach { it.disconnected() }
        }
        check(!actors.containsKey(source.id)) { "USB source is already active" }
        return UsbProducer(source.id, locator, target, failed, concurrent).also { actors[source.id] = it }
    }
    @Synchronized
    override fun release() {
        if (!closed.compareAndSet(false, true)) return
        try { watcher?.close() }
        finally {
            watcher = null
            actors.values.toList().forEach { it.stop() }
        }
    }

    private inner class UsbProducer(
        private val sourceId: String,
        private val locator: SceneUsbLocator,
        private val target: SceneCaptureTarget,
        private val failed: (Boolean) -> Unit,
        initialConcurrent: Boolean,
    ) : SceneProducer {
        private val stopped = AtomicBoolean(false)
        private val started = AtomicBoolean(false)
        private val queued = AtomicBoolean(false)
        private val attempts = AtomicInteger(0)
        private val demand = AtomicReference(Demand(target.frameRate, initialConcurrent))
        private val applied = AtomicReference<Demand?>(null)
        private val worker = workerFactory()
        @Volatile var deviceName: String? = null
            private set
        private var driver: SceneUsbDriver? = null // Worker-thread owned.
        private var modes = emptyList<SceneUsbMode>()
        private var configuration: SceneUsbConfiguration? = null

        override fun start() {
            check(!stopped.get() && started.compareAndSet(false, true))
            submit {
                if (stopped.get()) return@submit
                try {
                    if (!cameraAllowed()) throw SceneProducerPermissionException()
                    val selection = devices.resolve(locator)
                    check(selection is SceneUsbSelection.Selected) { "USB camera is missing or ambiguous" }
                    if (!selection.permissionGranted) throw SceneProducerPermissionException()
                    val device = selection.device
                    deviceName = device.deviceName
                    check(leases.putIfAbsent(device.deviceName, this) == null) {
                        "USB camera is already captured; duplicate its layer instead"
                    }
                    val capture = driverFactory(device)
                    driver = capture
                    modes = capture.open()
                    if (stopped.get()) { cleanup(); return@submit }
                    val requested = demand.get()
                    configuration = configure(capture, requested)
                    applied.set(requested)
                    if (stopped.get()) cleanup()
                } catch (error: Exception) { failOpen(error) }
                catch (error: LinkageError) { failOpen(error) }
            }
        }

        private fun cameraAllowed() = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        private fun failOpen(error: Throwable) {
            try { cleanup() }
            finally {
                if (!stopped.get()) {
                    Timber.w(error, "Could not start scene USB capture")
                    failed(error is SceneProducerPermissionException || !cameraAllowed())
                }
            }
        }
        private fun configure(capture: SceneUsbDriver, requested: Demand): SceneUsbConfiguration {
            val candidates = sceneUsbConfigurations(modes, target.width, target.height,
                requested.frameRate, requested.concurrent)
            check(candidates.isNotEmpty()) { "USB camera has no output within the capture budget" }
            var last: Exception? = null
            var touched = false
            for (candidate in candidates) {
                check(!stopped.get()) { "USB capture was stopped" }
                if (candidate == configuration && !touched) return candidate
                try {
                    touched = true
                    target.texture.setDefaultBufferSize(candidate.mode.width, candidate.mode.height)
                    capture.start(target.surface, candidate)
                    return candidate
                } catch (error: Exception) { last = error }
            }
            throw requireNotNull(last)
        }
        fun updateConcurrent(value: Boolean) = update(demand.get().copy(concurrent = value))
        fun updateRate(value: Int) = update(demand.get().copy(frameRate = value))
        private fun update(next: Demand) {
            if (stopped.get()) return
            if (demand.getAndSet(next) != next) attempts.set(0)
            if (!started.get() || applied.get() == next || attempts.get() >= 3 || !queued.compareAndSet(false, true)) return
            submit {
                try {
                    val capture = driver
                    if (!stopped.get() && capture != null) {
                        val requested = demand.get()
                        if (applied.get() != requested) {
                            val previous = configuration
                            try {
                                configuration = configure(capture, requested)
                                applied.set(requested)
                                attempts.set(0)
                            } catch (error: Exception) {
                                if (!stopped.get() && previous != null) {
                                    try {
                                        target.texture.setDefaultBufferSize(previous.mode.width, previous.mode.height)
                                        capture.start(target.surface, previous)
                                        if (attempts.incrementAndGet() >= 3) {
                                            applied.set(requested)
                                            Timber.w(error, "Could not change USB capture rate; retaining %d fps", previous.frameRate)
                                        }
                                    } catch (restore: Exception) { failOpen(restore) }
                                }
                            }
                        }
                    }
                } finally { queued.set(false) }
            }
        }
        fun disconnected() {
            if (!stopped.get()) {
                try { failed(false) } finally { stop() }
            }
        }
        private fun submit(action: () -> Unit) {
            try { worker.execute(action) }
            catch (_: RejectedExecutionException) { queued.set(false) }
        }
        private fun cleanup() {
            val capture = driver; driver = null
            try { capture?.close() }
            finally {
                deviceName?.let { leases.remove(it, this) }
                configuration = null
                modes = emptyList()
            }
        }
        override fun stop() {
            if (!stopped.compareAndSet(false, true)) return
            actors.remove(sourceId, this)
            try { submit(::cleanup) }
            finally { worker.shutdown() }
        }
        override fun release() = stop()
    }
}

private fun watchSceneUsbDetach(context: Context, detached: (String) -> Unit): Closeable {
    val app = context.applicationContext
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    ?.deviceName?.let(detached)
            }
        }
    }
    ContextCompat.registerReceiver(app, receiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
        ContextCompat.RECEIVER_NOT_EXPORTED)
    return Closeable { app.unregisterReceiver(receiver) }
}
