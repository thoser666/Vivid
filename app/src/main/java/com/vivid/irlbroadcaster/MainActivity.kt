package com.vivid.irlbroadcaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vivid.feature.playback.PlaybackScreen
import com.vivid.feature.settings.ui.SettingsScreen
import com.vivid.feature.streaming.ui.StreamingScreen
import com.vivid.feature.obscontrol.ui.ObsControlScreen
import com.vivid.irlbroadcaster.ui.theme.VividTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            StreamingScreen(navController = navController)
        }
        composable("playback/{streamUrl}") { backStackEntry ->
            val streamUrl = backStackEntry.arguments?.getString("streamUrl")
            PlaybackScreen(navController, streamUrl)
        }
        composable("settings_route") {
            SettingsScreen(navController = navController)
        }
        composable("obs_control") {
            ObsControlScreen()
        }
    }
}
