package com.vivid.irlbroadcaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vivid.BuildConfig
import com.vivid.feature.obscontrol.ui.ObsControlScreen
import com.vivid.feature.playback.PlaybackScreen
import com.vivid.feature.settings.ui.SettingsAboutScreen
import com.vivid.feature.settings.ui.SettingsChatBotScreen
import com.vivid.feature.settings.ui.SettingsOverlaysScreen
import com.vivid.feature.settings.ui.SettingsRemotePrivacyScreen
import com.vivid.feature.settings.ui.SettingsScreen
import com.vivid.feature.settings.ui.SettingsStreamingObsScreen
import com.vivid.feature.settings.ui.SettingsViewModel
import com.vivid.feature.streaming.ui.StreamingScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.irlbroadcaster.ui.about.AboutScreen
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
        composable(
            route = "settings_route?versionName={versionName}",
            arguments = listOf(
                navArgument("versionName") {
                    type = NavType.StringType
                    defaultValue = BuildConfig.VERSION_NAME
                },
            ),
        ) { backStackEntry ->
            SettingsScreen(
                navController = navController,
                installedVersionName = backStackEntry.arguments?.getString("versionName") ?: BuildConfig.VERSION_NAME,
            )
        }
        composable("settings_streaming") {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            SettingsStreamingObsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings_overlays") {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            SettingsOverlaysScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings_chatbot") {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val botUsage by viewModel.botUsage.collectAsState()
            SettingsChatBotScreen(
                uiState = uiState,
                viewModel = viewModel,
                botUsage = botUsage,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings_remote") {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val remoteControl by viewModel.remoteControl.collectAsState()
            SettingsRemotePrivacyScreen(
                uiState = uiState,
                viewModel = viewModel,
                remoteControl = remoteControl,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "settings_about?versionName={versionName}",
            arguments = listOf(
                navArgument("versionName") {
                    type = NavType.StringType
                    defaultValue = BuildConfig.VERSION_NAME
                },
            ),
        ) { backStackEntry ->
            val viewModel: SettingsViewModel = hiltViewModel()
            val updateState by viewModel.updateState.collectAsState()
            SettingsAboutScreen(
                installedVersionName = backStackEntry.arguments?.getString("versionName")
                    ?: BuildConfig.VERSION_NAME,
                updateState = updateState,
                onOpenAbout = { navController.navigate("about_route") },
                onBack = { navController.popBackStack() },
            )
        }
        composable("obs_control") {
            ObsControlScreen()
        }
        composable("about_route") {
            AboutScreen(navController = navController)
        }
    }
}
