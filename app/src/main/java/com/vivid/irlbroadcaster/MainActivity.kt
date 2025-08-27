package com.vivid.irlbroadcaster

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vivid.feature.playback.PlaybackScreen
import com.vivid.feature.streaming.ui.StreamingScreen // Import the streaming screen
import com.vivid.irlbroadcaster.ui.theme.VividTheme
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry

@AndroidEntryPoint
class MainActivity : ComponentActivity() {


    private lateinit var viewFinder: PreviewView

    // ActivityResultLauncher für die Berechtigungsanfrage
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                // Erkläre dem Nutzer, warum die Berechtigung benötigt wird
            }
        }
/*
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)

        // Prüfe und fordere die Berechtigung an
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            activityResultLauncher.launch(Manifest.permission.CAMERA)
        }
    }
*/
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // CameraProvider ist jetzt verfügbar
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 1. Preview Use Case erstellen
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            // 2. Kamera auswählen (hier: Rückkamera)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Vorherige Bindungen aufheben
                cameraProvider.unbindAll()

                // 3. Use Cases an den Lebenszyklus der Kamera binden
                // Das ist die Magie von CameraX! ✨
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview)

            } catch(exc: Exception) {
                // Fehler behandeln, z.B. wenn keine Kamera verfügbar ist
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // waiting for view to draw to better represent a captured error with a screenshot
        findViewById<android.view.View>(android.R.id.content).viewTreeObserver.addOnGlobalLayoutListener {
            try {
                throw Exception("This app uses Sentry! :)")
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }

        setContent {
            VividTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    VividAppNavigation()
                }
            }
        }
    }
}

@Composable
fun VividAppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "streaming_route") {
        composable("streaming_route") {
            StreamingScreen(navController = navController) // Pass navController
        }
        composable("playback/{streamUrl}") { backStackEntry ->
            val streamUrl = backStackEntry.arguments?.getString("streamUrl")
            PlaybackScreen(navController, streamUrl)
        }
        // TODO: Add more navigation routes for other feature modules (chat, settings, widgets)
        // composable("chat_route") { ChatScreen(navController = navController) }
        // composable("settings_route") { SettingsScreen(navController = navController) }
    }


}
