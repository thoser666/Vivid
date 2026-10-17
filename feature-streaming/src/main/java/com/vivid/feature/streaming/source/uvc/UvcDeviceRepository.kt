package com.vivid.feature.streaming.source.uvc

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schmale Abstraktion über [UsbManager]/[PackageManager] (injizierbar für Tests).
 *
 * Die Aufzählung nutzt ausschließlich `UsbManager.deviceList` — keine
 * Netzwerkzugriffe, keine Vendor/Product-Allowlist, keine Gerätepfad-Tricks.
 */
interface UsbHostGateway {
    fun attachedDevices(): List<UsbDevice>
    fun hasPermission(device: UsbDevice): Boolean
    fun hasUsbHostFeature(): Boolean
}

/** Produktionsimplementierung über den Android-USB-Host. */
class AndroidUsbHostGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsbHostGateway {
    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    override fun attachedDevices(): List<UsbDevice> =
        runCatching { usbManager?.deviceList?.values?.toList().orEmpty() }
            .getOrDefault(emptyList())

    override fun hasPermission(device: UsbDevice): Boolean =
        usbManager?.hasPermission(device) == true

    override fun hasUsbHostFeature(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
}

/**
 * Zählt angeschlossene USB-Videogeräte auf (#305, Phase B).
 *
 * Erkennung auf Interface-Ebene: Viele Composite-Geräte melden Geräteklasse 0 —
 * erst die Interface-Klasse 14 ([UsbConstants.USB_CLASS_VIDEO]) belegt UVC.
 * Herstellerspezifische Capture-Cards (Interface-Klasse 255,
 * [UsbConstants.USB_CLASS_VENDOR_SPEC]) werden als *unverifizierte* Kandidaten
 * gelistet (`verifiedVideoClass = false`) und erst beim Öffnen gegen die
 * tatsächlichen UVC-Fähigkeiten validiert ([UvcConnectionManager]).
 *
 * Nur sichere Felder werden übernommen: VID/PID + Produkt-/Herstellername.
 * Seriennummern werden nie gelesen, nie persistiert und nie geloggt.
 */
@Singleton
class UvcDeviceRepository @Inject constructor(
    private val gateway: UsbHostGateway,
) {
    private val _devices = MutableStateFlow<List<UvcDeviceDescriptor>>(emptyList())

    /** Zuletzt aufgezählte Geräte (Anlage-Reihenfolge, stabil pro Refresh). */
    val devices: StateFlow<List<UvcDeviceDescriptor>> = _devices.asStateFlow()

    /** Rohe Geräte-Handles des letzten Refresh (Schlüssel: [UvcDeviceDescriptor.stableSessionId]). */
    private val sessionIds = mutableMapOf<String, String>()
    private var deviceHandles: Map<String, UsbDevice> = emptyMap()

    fun hasUsbHostFeature(): Boolean = gateway.hasUsbHostFeature()

    /**
     * Zählt neu auf und publiziert die Liste. Gibt die Liste zurück.
     * Each attachment receives a runtime UUID. Removing an identical peer never
     * changes the surviving device's identity. Only VID:PID and mode are persisted.
     */
    fun refresh(): List<UvcDeviceDescriptor> {
        val usbDevices = gateway.attachedDevices().filter(::isUvcCandidate)
        sessionIds.keys.retainAll(usbDevices.map { it.deviceName }.toSet())
        val grouped = usbDevices.groupBy { it.vendorId to it.productId }
        val descriptors = mutableListOf<UvcDeviceDescriptor>()
        val handles = mutableMapOf<String, UsbDevice>()
        grouped.forEach { (vidPid, devices) ->
            devices.sortedBy { it.deviceName }.forEachIndexed { index, device ->
                val sessionId = sessionIds.getOrPut(device.deviceName) { java.util.UUID.randomUUID().toString() }
                val label = device.productName?.takeIf { it.isNotBlank() }
                    ?: "%04X:%04X".format(vidPid.first, vidPid.second)
                descriptors += UvcDeviceDescriptor(
                    stableSessionId = sessionId,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    productLabel = label,
                    manufacturerLabel = device.manufacturerName?.takeIf { it.isNotBlank() },
                    verifiedVideoClass = hasVideoClassInterface(device),
                )
                handles[sessionId] = device
            }
        }
        deviceHandles = handles
        _devices.value = descriptors.sortedBy { it.stableSessionId }
        // Nur Zähler + VID:PID-Klassen loggen — keine Namen, Pfade oder Serien.
        Timber.i(
            "UvcDeviceRepository: %d UVC-Kandidat(en) aufgezählt",
            descriptors.size,
        )
        return _devices.value
    }

    /** Roher Geräte-Handle für [sessionId] (null, wenn nicht mehr angeschlossen). */
    fun deviceHandle(sessionId: String): UsbDevice? =
        deviceHandles[sessionId]?.takeIf { current ->
            gateway.attachedDevices().any { it.deviceName == current.deviceName }
        }

    /** Gerätedeskriptor für [sessionId] aus dem letzten Refresh (null, wenn unbekannt). */
    fun descriptor(sessionId: String): UvcDeviceDescriptor? =
        _devices.value.firstOrNull { it.stableSessionId == sessionId }

    fun hasPermission(device: UsbDevice): Boolean = gateway.hasPermission(device)

    companion object {
        /**
         * Listungskandidat: echte Videoklasse ODER herstellerspezifisches
         * Composite (Capture-Card-Verdacht — Validierung erst beim Öffnen).
         */
        fun isUvcCandidate(device: UsbDevice): Boolean =
            runCatching {
                if (device.deviceClass == UsbConstants.USB_CLASS_VIDEO) return true
                (0 until device.interfaceCount).any { index ->
                    val interfaceClass = device.getInterface(index).interfaceClass
                    interfaceClass == UsbConstants.USB_CLASS_VIDEO ||
                        interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
                }
            }.getOrDefault(false)

        /** Echte USB-Videoklasse (Gerät oder mindestens ein Interface). */
        fun hasVideoClassInterface(device: UsbDevice): Boolean =
            runCatching {
                if (device.deviceClass == UsbConstants.USB_CLASS_VIDEO) return true
                (0 until device.interfaceCount).any { index ->
                    device.getInterface(index).interfaceClass == UsbConstants.USB_CLASS_VIDEO
                }
            }.getOrDefault(false)
    }
}
