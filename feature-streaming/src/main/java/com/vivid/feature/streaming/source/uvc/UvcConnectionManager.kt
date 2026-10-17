package com.vivid.feature.streaming.source.uvc

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.view.Surface
import androidx.core.content.ContextCompat
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

fun interface UvcGatewayFactory { fun create(): UvcCameraGateway }
class HerohanUvcGatewayFactory @Inject constructor() : UvcGatewayFactory {
    override fun create(): UvcCameraGateway = HerohanUvcCameraGateway()
}

/** One application owner. Lock serializes ownership; generation rejects callbacks from retired helpers. */
@Singleton
class UvcConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UvcDeviceRepository,
    private val permission: UvcPermissionController,
    private val gatewayFactory: UvcGatewayFactory,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()
    private var generation = 0
    private var gateway: UvcCameraGateway? = null
    private var selected: UsbDevice? = null
    private var descriptor: UvcDeviceDescriptor? = null
    private var requestedMode: UvcCaptureMode? = null
    private var openedMode: UvcCaptureMode? = null
    private var captureSurface: Surface? = null
    private var captureGeneration = 0
    private val preferences = mutableMapOf<String, UvcCaptureMode>()
    private val _devices = MutableStateFlow<List<UvcDeviceDescriptor>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _state = MutableStateFlow<UvcConnectionState>(UvcConnectionState.Disconnected)
    val connectionState = _state.asStateFlow()
    private val _notice = MutableStateFlow<UvcFailure?>(null)
    val notice = _notice.asStateFlow()
    val isCaptureReady: Boolean get() = _state.value.let {
        it is UvcConnectionState.Ready || it is UvcConnectionState.Starting || it is UvcConnectionState.Capturing
    }
    val isCaptureSurfaceActive: Boolean get() = synchronized(lock) { captureSurface != null }
    @Volatile var onCaptureLost: (() -> Unit)? = null
    fun hasUsbHostFeature(): Boolean = repository.hasUsbHostFeature()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> refresh()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> if (device != null) detached(device)
            }
        }
    }

    init {
        permission.resultListener = ::permissionResult
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        // Receiver intentionally lives for this application singleton, never retains an Activity.
        scope.launch {
            val stored = settingsRepository.uvcPreferenceFlow().first()
            val mode = UvcCaptureMode.parsePreference(stored.second)
            if (stored.first != null && mode != null) synchronized(lock) { preferences[stored.first!!] = mode }
        }
        refresh()
    }

    fun refresh() { synchronized(lock) { _devices.value = repository.refresh() } }

    fun connect(device: UvcDeviceDescriptor, mode: UvcCaptureMode? = null) {
        synchronized(lock) {
            if (captureSurface != null) { _notice.value = UvcFailure.DeviceBusy; return }
            teardownLocked()
            if (!repository.hasUsbHostFeature()) { failLocked(UvcFailure.UnsupportedHost); return }
            // Android P+ requires CAMERA for USB video-class permission, separately from the USB grant.
            if (android.os.Build.VERSION.SDK_INT >= 28 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ) { failLocked(UvcFailure.CameraPermissionDenied); return }
            val handle = repository.deviceHandle(device.stableSessionId)
            if (handle == null) { failLocked(UvcFailure.OpenFailed); return }
            selected = handle
            descriptor = device
            requestedMode = mode ?: preferences[device.preferenceKey()]
            _notice.value = null
            _state.value = UvcConnectionState.PermissionRequired(device)
            if (permission.request(handle)) openLocked()
            else if (!permission.hasPending()) failLocked(UvcFailure.PermissionDenied)
        }
    }

    fun setMode(mode: UvcCaptureMode) {
        synchronized(lock) {
            val device = descriptor ?: (_state.value as? UvcConnectionState.Error)?.device ?: return
            if (captureSurface != null) { _notice.value = UvcFailure.DeviceBusy; return }
            if (mode !in device.modes) { _notice.value = UvcFailure.UnsupportedMode; return }
            // A fresh helper per mode eliminates late close/open callbacks from an earlier negotiation.
            connect(device, mode)
        }
    }

    private fun permissionResult(device: UsbDevice, granted: Boolean) {
        synchronized(lock) {
            if (!matches(device) || _state.value !is UvcConnectionState.PermissionRequired) return
            if (granted && repository.hasPermission(device)) openLocked()
            else failLocked(UvcFailure.PermissionDenied)
        }
    }

    private fun openLocked() {
        val device = selected ?: return
        val description = descriptor ?: return
        val gen = generation
        _state.value = UvcConnectionState.Opening(description)
        try {
            gateway = gatewayFactory.create()
            gateway?.setListener(gatewayListener(gen))
            gateway?.select(device)
        } catch (_: LinkageError) { failLocked(UvcFailure.NativeLibraryMissing) }
        catch (_: UvcNativeLibraryException) { failLocked(UvcFailure.NativeLibraryMissing) }
        catch (_: Exception) { failLocked(UvcFailure.OpenFailed) }
        scope.launch {
            delay(OPEN_TIMEOUT_MS)
            synchronized(lock) {
                if (gen == generation && _state.value is UvcConnectionState.Opening) failLocked(UvcFailure.OpenFailed)
            }
        }
    }

    internal fun gatewayListener(gen: Int) = object : UvcCameraGateway.Listener {
        override fun onAttach(device: UsbDevice) = Unit
        override fun onDeviceOpen(device: UsbDevice, firstOpen: Boolean) {
            synchronized(lock) {
                if (!valid(gen, device) || _state.value !is UvcConnectionState.Opening) return
                // USB access is open, but UVCAndroid has not opened its native
                // camera yet; the descriptor mode list is only available after
                // the subsequent onCameraOpen callback.
                try { gateway?.open(null) } catch (_: Exception) { failLocked(UvcFailure.OpenFailed) }
            }
        }
        override fun onCameraOpen(device: UsbDevice) {
            synchronized(lock) {
                if (!valid(gen, device) || _state.value !is UvcConnectionState.Opening) return
                val description = descriptor ?: return
                val modes = mapUvcModes(gateway?.supportedFormats().orEmpty())
                val reported = gateway?.actualMode()
                val preferred = requestedMode?.takeIf { it in modes }
                val mode = preferred
                    ?: reported?.takeIf { it in modes }
                    ?: selectDefaultUvcMode(modes)
                if (mode == null) {
                    failLocked(UvcFailure.ModeNegotiationFailed)
                    return
                }
                if (requestedMode != null && requestedMode != mode) _notice.value = UvcFailure.UnsupportedMode
                descriptor = description.copy(modes = modes)
                if (reported != mode) {
                    // UVCAndroid's previewSize setter posts to its own Handler and
                    // silently swallows native negotiation failures. Do not publish
                    // Ready until the gateway reads back the negotiated mode.
                    try {
                        gateway?.open(mode) ?: failLocked(UvcFailure.OpenFailed)
                    } catch (_: LinkageError) {
                        failLocked(UvcFailure.NativeLibraryMissing)
                    } catch (_: Exception) {
                        failLocked(UvcFailure.ModeNegotiationFailed)
                    }
                    return
                }
                completeOpenLocked(mode)
            }
        }
        override fun onModeConfigured(device: UsbDevice, mode: UvcCaptureMode) {
            synchronized(lock) {
                if (!valid(gen, device) || _state.value !is UvcConnectionState.Opening) return
                if (mode !in descriptor?.modes.orEmpty()) {
                    failLocked(UvcFailure.ModeNegotiationFailed)
                    return
                }
                completeOpenLocked(mode)
            }
        }
        override fun onFirstFrame(device: UsbDevice) {
            synchronized(lock) {
                if (!valid(gen, device) || captureSurface == null) return
                val state = _state.value as? UvcConnectionState.Starting ?: return
                _state.value = UvcConnectionState.Capturing(state.device, state.mode)
            }
        }
        override fun onCameraClose(device: UsbDevice) { unexpectedClose(device, gen) }
        override fun onDeviceClose(device: UsbDevice) { unexpectedClose(device, gen) }
        override fun onDetach(device: UsbDevice) { if (synchronized(lock) { valid(gen, device) }) detached(device) }
        override fun onCancel(device: UsbDevice) = onError(device, UvcFailure.PermissionDenied)
        override fun onError(device: UsbDevice, failure: UvcFailure) { lost(device, gen, failure) }
    }

    private fun completeOpenLocked(mode: UvcCaptureMode) {
        val device = descriptor ?: return
        openedMode = mode
        _state.value = UvcConnectionState.Ready(device, mode)
        preferences[device.preferenceKey()] = mode
        scope.launch {
            settingsRepository.updateUvcPreference(device.preferenceKey(), mode.preferenceString())
        }
    }

    private fun unexpectedClose(device: UsbDevice, gen: Int) { lost(device, gen, UvcFailure.OpenFailed) }
    private fun lost(device: UsbDevice, gen: Int, failure: UvcFailure) {
        val notify = synchronized(lock) {
            if (!valid(gen, device)) return
            val active = captureSurface != null
            failLocked(failure)
            active
        }
        if (notify) onCaptureLost?.invoke()
    }

    private fun detached(device: UsbDevice) {
        val notify = synchronized(lock) {
            if (!matches(device)) { refresh(); return }
            val active = captureSurface != null
            permission.clearPending(device)
            _notice.value = null
            val lastKnownDevice = descriptor
            teardownLocked()
            _state.value = if (active) {
                UvcConnectionState.Error(UvcFailure.DetachedDuringCapture, lastKnownDevice)
            } else {
                UvcConnectionState.Disconnected
            }
            refresh()
            active
        }
        if (notify) onCaptureLost?.invoke()
    }

    fun attachCaptureSurface(surface: Surface): Boolean = synchronized(lock) {
        val state = _state.value as? UvcConnectionState.Ready ?: return false
        val camera = gateway ?: return false
        captureSurface = surface
        val captureGen = ++captureGeneration
        _state.value = UvcConnectionState.Starting(state.device, state.mode)
        try {
            if (!camera.addSurface(surface) || !camera.startPreview()) {
                failLocked(UvcFailure.PreviewFailed)
                return false
            }
        } catch (_: LinkageError) { failLocked(UvcFailure.NativeLibraryMissing); return false }
        catch (_: Exception) { failLocked(UvcFailure.PreviewFailed); return false }
        val gen = generation
        scope.launch {
            delay(OPEN_TIMEOUT_MS)
            if (synchronized(lock) { gen == generation && captureGen == captureGeneration && _state.value is UvcConnectionState.Starting }) {
                selected?.let { lost(it, gen, UvcFailure.PreviewFailed) }
            }
        }
        true
    }

    fun detachCaptureSurface(surface: Surface) {
        synchronized(lock) {
            if (captureSurface !== surface) return
            captureSurface = null
            captureGeneration++
            cleanup { gateway?.removeSurface(surface) }
            cleanup { gateway?.stopPreview() }
            val device = descriptor
            val mode = openedMode
            if (device != null && mode != null) _state.value = UvcConnectionState.Ready(device, mode)
        }
    }

    fun disconnect() {
        val notify = synchronized(lock) {
            val active = captureSurface != null
            teardownLocked()
            _notice.value = null
            _state.value = UvcConnectionState.Disconnected
            active
        }
        if (notify) onCaptureLost?.invoke()
    }

    private fun matches(device: UsbDevice) = selected?.deviceName == device.deviceName
    private fun valid(gen: Int, device: UsbDevice) = gen == generation && matches(device)
    private fun failLocked(failure: UvcFailure) {
        val lastKnownDevice = descriptor?.let { it.copy(modes = it.modes.toList()) }
        teardownLocked()
        // The state is the single source of truth for terminal errors. Keeping a
        // duplicate notice made the same failure render twice in the picker.
        _notice.value = null
        _state.value = UvcConnectionState.Error(failure, lastKnownDevice)
    }
    private fun teardownLocked() {
        generation++
        captureGeneration++
        selected?.let(permission::clearPending)
        val old = gateway
        gateway = null
        old?.setListener(null)
        captureSurface?.let { surface -> cleanup { old?.removeSurface(surface) } }
        cleanup { old?.stopPreview() }
        cleanup { old?.closeCamera() }
        cleanup { old?.release() }
        selected = null
        descriptor = null
        captureSurface = null
        requestedMode = null
        openedMode = null
    }
    private inline fun cleanup(block: () -> Unit) {
        try { block() } catch (_: Exception) { Timber.w("UVC resource cleanup failed") }
    }
    companion object { private const val OPEN_TIMEOUT_MS = 10_000L }
}
