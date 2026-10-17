package com.vivid.feature.streaming.source.uvc

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schmale Abstraktion über die USB-Berechtigung (injizierbar für Tests).
 */
interface UsbPermissionGateway {
    fun hasPermission(device: UsbDevice): Boolean
    fun requestPermission(device: UsbDevice, intent: PendingIntent)
}

/** Produktionsimplementierung über [UsbManager]. */
class AndroidUsbPermissionGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsbPermissionGateway {
    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    override fun hasPermission(device: UsbDevice): Boolean =
        usbManager?.hasPermission(device) == true

    override fun requestPermission(device: UsbDevice, intent: PendingIntent) {
        val manager = checkNotNull(usbManager) { "USB manager unavailable" }
        manager.requestPermission(device, intent)
    }
}

/**
 * USB-Berechtigungsablauf (#305, Phase B).
 *
 * Regeln:
 * - Angeboten wird die Berechtigung nur nach expliziter Nutzeraktion
 *   ([request] wird ausschließlich aus dem Geräte-Picker aufgerufen) — niemals
 *   beim bloßen Öffnen des Screens.
 * - Bereits erteilte Berechtigung wird wiederverwendet (kein Doppel-Dialog;
 *   der UVC-Helper fragt ebenfalls nur bei fehlender Berechtigung).
 * - Ergebnisse ohne ausstehende Anfrage oder mit fremdem Gerät werden als
 *   verspätet ignoriert (Stale-Grant) — kein blinder Re-Open eines
 *   unverwandten Geräts.
 * - Das `PendingIntent` ist paket-skopiert und `FLAG_MUTABLE` (API 31+/34+:
 *   das USB-Framework füllt das Grant-Ergebnis ein); der Empfänger wird
 *   nicht-exportiert registriert ([UvcConnectionManager]).
 *
 * Android P+ requires runtime CAMERA for video-class USB devices in addition
 * to the USB grant (UsbManager.requestPermission documentation). The picker
 * requests CAMERA only after Connect is pressed.
 */
@Singleton
class UvcPermissionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateway: UsbPermissionGateway,
) {
    private val _pendingDevice = MutableStateFlow<UsbDevice?>(null)

    /** Gerät mit ausstehender Berechtigungsanfrage (null = keine). */
    val pendingDevice: StateFlow<UsbDevice?> = _pendingDevice.asStateFlow()

    /**
     * Empfänger des Grant-Ergebnisses (setzt [UvcConnectionManager]).
     * Wird genau einmal pro abgeschlossener Anfrage aufgerufen.
     */
    private var requestToken: String? = null

    var resultListener: ((device: UsbDevice, granted: Boolean) -> Unit)? = null

    /**
     * Fragt die Berechtigung für [device] an.
     *
     * @return true, wenn die Berechtigung bereits erteilt ist (kein Dialog nötig).
     */
    @Synchronized
    fun request(device: UsbDevice): Boolean {
        if (gateway.hasPermission(device)) {
            _pendingDevice.value = null
            requestToken = null
            return true
        }
        if (_pendingDevice.value?.deviceName == device.deviceName) return false
        _pendingDevice.value = device
        requestToken = java.util.UUID.randomUUID().toString()
        runCatching { gateway.requestPermission(device, permissionIntent()) }
            .onFailure { error ->
                Timber.e(error, "UvcPermissionController: Anfrage fehlgeschlagen")
                _pendingDevice.value = null
                requestToken = null
            }
        return false
    }

    /**
     * Verarbeitet das Grant-Ergebnis aus dem USB-Broadcast.
     *
     * @return true, wenn das Ergebnis zu einer ausstehenden Anfrage gehörte.
     */
    fun onResult(device: UsbDevice, granted: Boolean): Boolean =
        completeResult(device, granted, expectedToken = null)

    private fun completeResult(device: UsbDevice, granted: Boolean, expectedToken: String?): Boolean {
        val callback = synchronized(this) {
            val pending = _pendingDevice.value ?: return false
            if (pending.deviceName != device.deviceName) return false
            // Validate and consume the token in the SAME critical section.
            // A stale grant for the same reconnected device cannot complete a newer request.
            if (expectedToken != null && requestToken != expectedToken) return false
            _pendingDevice.value = null
            requestToken = null
            resultListener
        }
        // Never invoke callbacks while holding the permission monitor.
        callback?.invoke(device, granted)
        return true
    }

    /** Verwirft eine ausstehende Anfrage (z. B. Gerät währenddessen entfernt). */
    @Synchronized
    fun clearPending(device: UsbDevice) {
        if (_pendingDevice.value?.deviceName == device.deviceName) {
            _pendingDevice.value = null
            requestToken = null
        }
    }

    @Synchronized
    fun hasPending(): Boolean = _pendingDevice.value != null

    private fun permissionIntent(): PendingIntent {
        // Use an explicit manifest receiver. UsbManager delivers this callback
        // through a system-owned PendingIntent; Android may omit privileged
        // broadcasts from dynamically registered RECEIVER_NOT_EXPORTED
        // receivers. The receiver itself remains non-exported, and the random
        // URI token still rejects stale grants from earlier sessions.
        val intent = Intent(context, UvcUsbPermissionReceiver::class.java)
            .setAction(ACTION_USB_PERMISSION)
            .setPackage(context.packageName)
            .setData("vivid-uvc://permission/$requestToken".toUri())
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    fun acceptBroadcast(intent: Intent, device: UsbDevice): Boolean {
        val token = intent.data?.lastPathSegment ?: return false
        return completeResult(device,
            intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false),
            expectedToken = token,
        )
    }

    companion object {
        const val ACTION_USB_PERMISSION =
            "com.vivid.feature.streaming.source.uvc.USB_PERMISSION"
    }
}
