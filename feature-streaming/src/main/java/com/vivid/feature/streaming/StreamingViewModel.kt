package com.vivid.feature.streaming

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// Hilt für die automatische Bereitstellung des ViewModels
@HiltViewModel
class StreamingViewModel @Inject constructor() : ViewModel() {

    // Zustand für die Kamera-Vorschau
    private val _preview = MutableStateFlow<Preview?>(null)
    val preview = _preview.asStateFlow()

    // Zustand für die Kameraauswahl (Front-/Rückkamera)
    private val _cameraSelector = MutableStateFlow(CameraSelector.DEFAULT_BACK_CAMERA)
    val cameraSelector = _cameraSelector.asStateFlow()

    // Funktion zum Initialisieren und Starten der Kamera
    fun startCamera(context: Context, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        viewModelScope.launch {
            // CameraProvider holen und an den Lifecycle binden
            val cameraProvider = getCameraProvider(context)
            val previewUseCase = Preview.Builder().build()

            try {
                // Unbind everything before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    _cameraSelector.value,
                    previewUseCase
                )
                _preview.value = previewUseCase
            } catch (exc: Exception) {
                // Log error
            }
        }
    }

    // Funktion zum Wechseln der Kamera
    fun switchCamera() {
        _cameraSelector.value = if (_cameraSelector.value == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        // Hier muss startCamera erneut aufgerufen werden, um die Änderung anzuwenden.
        // Dies wird im StreamingScreen behandelt.
    }

    // Hilfsfunktion, um den CameraProvider asynchron zu erhalten
    private suspend fun getCameraProvider(context: Context): ProcessCameraProvider = suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                continuation.resume(future.get())
            }, ContextCompat.getMainExecutor(context))
        }
    }
}