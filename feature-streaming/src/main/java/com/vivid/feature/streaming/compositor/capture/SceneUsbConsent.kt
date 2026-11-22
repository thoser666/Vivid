package com.vivid.feature.streaming.compositor.capture

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.util.UUID

internal data class SceneUsbConsentResult(val deviceName: String, val granted: Boolean)

/** One user-initiated request, retained across Activity recreation without retaining that Activity. */
internal class SceneUsbConsent(context: Context) : Closeable {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val devices = SceneUsbDevices(app)
    private val action = "${app.packageName}.SCENE_USB_PERMISSION.${UUID.randomUUID()}"
    private val _result = MutableStateFlow<SceneUsbConsentResult?>(null)
    val result: StateFlow<SceneUsbConsentResult?> = _result.asStateFlow()
    @Volatile var requested = false
        private set
    private var closed = false
    private var registered = false
    private var target: UsbDevice? = null
    private var name = ""
    private var pending: PendingIntent? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                action -> completeFromCurrentPermission()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> IntentCompat.getParcelableExtra(
                    intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)?.let(::detached)
            }
        }
    }

    /** CAMERA consent is requested by the Activity before entering the USB permission flow. */
    @Synchronized
    fun request(deviceName: String) {
        if (closed || requested) return
        requested = true
        name = deviceName
        target = devices.deviceNamed(deviceName)
        if (target == null || !hasCameraPermission()) { finish(false); return }
        if (devices.hasPermission(requireNotNull(target))) { finish(true); return }
        try {
            ContextCompat.registerReceiver(app, receiver, IntentFilter(action).apply {
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            // We check UsbManager's real grant, so mutable fill-in extras are unnecessary.
            val callback = PendingIntent.getBroadcast(app, 0, Intent(action).setPackage(app.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            pending = callback
            requireNotNull(manager).requestPermission(requireNotNull(target), callback)
        } catch (_: RuntimeException) { finish(false) }
    }

    /** After process death a lost request is cancelled; an already issued real grant is retained. */
    @Synchronized
    fun restore(deviceName: String) {
        if (closed || requested) return
        requested = true
        name = deviceName
        target = devices.deviceNamed(deviceName)
        completeFromCurrentPermission()
    }

    @Synchronized
    private fun detached(device: UsbDevice) {
        if (!closed && _result.value == null && device.deviceName == target?.deviceName) finish(false)
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    @Synchronized
    private fun completeFromCurrentPermission() {
        if (closed || _result.value != null) return
        val original = target
        val current = devices.deviceNamed(name)
        finish(original != null && current != null && original.vendorId == current.vendorId &&
            original.productId == current.productId && hasCameraPermission() && devices.hasPermission(current))
    }

    private fun finish(granted: Boolean) {
        cleanup()
        _result.value = SceneUsbConsentResult(name, granted)
    }

    private fun cleanup() {
        if (registered) {
            registered = false
            try { app.unregisterReceiver(receiver) } catch (error: IllegalArgumentException) { timber.log.Timber.d(error, "Scene USB consent receiver was already unregistered") }
        }
        pending?.cancel()
        pending = null
    }

    @Synchronized
    override fun close() {
        closed = true
        cleanup()
    }
}
