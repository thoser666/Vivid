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
import com.vivid.feature.streaming.StreamingScreen // Import the streaming screen
import com.vivid.irlbroadcaster.ui.theme.VividTheme

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
            StreamingScreen(navController = navController) // Pass navController
        }
        // TODO: Add more navigation routes for other feature modules (chat, settings, widgets)
        // composable("chat_route") { ChatScreen(navController = navController) }
        // composable("settings_route") { SettingsScreen(navController = navController) }
    }
}
