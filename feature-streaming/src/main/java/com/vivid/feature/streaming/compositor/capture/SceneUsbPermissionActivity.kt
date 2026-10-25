package com.vivid.feature.streaming.compositor.capture

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.UUID

/** Internal ActivityResult bridge: Android owns both localized permission dialogs. */
class SceneUsbPermissionActivity : ComponentActivity() {
    private lateinit var model: SceneUsbPermissionModel
    private var deviceName: String? = null
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestUsb() else { setResult(RESULT_CANCELED); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
        if (deviceName.isNullOrBlank()) { finish(); return }
        model = ViewModelProvider(this)[SceneUsbPermissionModel::class.java]
        lifecycleScope.launch {
            model.consent.result.filterNotNull().collect { result ->
                setResult(if (result.granted) RESULT_OK else RESULT_CANCELED,
                    Intent().putExtra(EXTRA_DEVICE_NAME, result.deviceName))
                finish()
            }
        }
        if (savedInstanceState != null && savedInstanceState.getString(STATE_MODEL) != model.identity) {
            model.consent.restore(requireNotNull(deviceName))
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            requestUsb()
        } else if (savedInstanceState == null) cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun requestUsb() {
        if (!isFinishing && ::model.isInitialized) deviceName?.let(model.consent::request)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::model.isInitialized) outState.putString(STATE_MODEL, model.identity)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_DEVICE_NAME = "scene_usb_device_name"
        private const val STATE_MODEL = "scene_usb_permission_model"
    }
}

internal class SceneUsbPermissionModel(app: Application) : AndroidViewModel(app) {
    val identity: String = UUID.randomUUID().toString()
    val consent = SceneUsbConsent(app)
    override fun onCleared() { consent.close() }
}
