package com.vivid.feature.streaming.source.uvc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives UsbManager's explicit PendingIntent grant result. */
@AndroidEntryPoint
class UvcUsbPermissionReceiver : BroadcastReceiver() {
    @Inject lateinit var permissionController: UvcPermissionController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UvcPermissionController.ACTION_USB_PERMISSION) return
        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        } ?: return
        permissionController.acceptBroadcast(intent, device)
    }
}
