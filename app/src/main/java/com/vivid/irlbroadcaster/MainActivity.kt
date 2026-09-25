package com.vivid.irlbroadcaster

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vivid.BuildConfig
import com.vivid.core.startup.CrashLoopGuard
import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.ThemeMode
import com.vivid.core.data.resolveDark
import com.vivid.core.ui.LocalWindowWidthClass
import com.vivid.feature.obscontrol.ui.ObsControlScreen
import com.vivid.feature.playback.PlaybackScreen
import com.vivid.feature.streaming.ui.ReplayLibraryScreen
import com.vivid.feature.settings.ui.SettingsAboutScreen
import com.vivid.feature.settings.ui.SettingsAppearanceScreen
import com.vivid.feature.settings.ui.SettingsCameraScreen
import com.vivid.feature.settings.ui.SettingsChatBotScreen
import com.vivid.feature.settings.ui.SettingsLogsScreen
import com.vivid.feature.settings.ui.SettingsOverlaysScreen
import com.vivid.feature.settings.ui.SettingsRemotePrivacyScreen
import com.vivid.feature.settings.ui.SettingsScreen
import com.vivid.feature.settings.ui.SettingsStreamingObsScreen
import com.vivid.feature.settings.ui.SettingsViewModel
import com.vivid.feature.chat.twitch.TwitchChannelViewModel
import com.vivid.feature.streaming.ui.StreamingScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.irlbroadcaster.ui.about.AboutScreen
import com.vivid.irlbroadcaster.ui.help.HelpScreen
import com.vivid.irlbroadcaster.ui.theme.VividTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var crashLoopGuard: CrashLoopGuard

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-Edge (targetSdk 37: ab SDK 35 vom System erzwungen): Die
        // App zeichnet hinter Status-/Navigationsleiste; die Inset-Behandlung
        // läuft über die M3-Scaffolds (paddingValues) und die Custom-Bars.
        enableEdgeToEdge()
        setContent {
            // Darstellung (Settings-Kategorie „Darstellung“): Design-Modus
            // (System/Hell/Dunkel/AMOLED) + Akzentfarbe live anwenden — das
            // Theme reagiert sofort, ohne App-Neustart.
            val settings by settingsRepository.appSettingsFlow.collectAsState(initial = AppSettings())
            val systemDark = isSystemInDarkTheme()
            val dark = settings.themeMode.resolveDark(systemDark)
            // Window Size Class (M3): Einmal pro Activity-Erstellung berechnet
            // - bei Rotation/Faltung wird die Activity neu erstellt und die
            // Klasse korrekt neu abgeleitet. Screens lesen sie ueber die
            // Modul-Naht LocalWindowWidthClass (core-ui).
            //
            // Robustheit statt M3-calculateWindowSizeClass (Startcrash S23/
            // API 34): M3 laesst androidx.window intern die OEM-Vendor-Klassen
            // androidx.window.extensions.* / androidx.window.sidecar.* aufloesen.
            // Fehlen diese auf dem Geraet (z.B. S23/OneUI, API-34-google_apis-
            // Emulator ohne Window-Extensions-Provider), wirft die Aufloesung
            // einen NoClassDefFoundError beim Activity-Start (Startcrash,
            // reproduziert im Emulator-Gate). Try/catch um Composable-Aufrufe
            // verbietet der Compose-Compiler, daher leitet
            // resolveSafeWindowWidthSizeClass die Breite direkt aus der
            // Framework-Metrik ab (ohne Vendor-Provider liefert M3 ohnehin
            // genau das) - identische Schwellen (600/840 dp).
            val viewWidthSizeClass = resolveSafeWindowWidthSizeClass(this@MainActivity)
            CompositionLocalProvider(
                LocalWindowWidthClass provides viewWidthSizeClass,
            ) {
            VividTheme(
                darkTheme = dark,
                amoled = settings.themeMode == ThemeMode.AMOLED,
                accent = settings.themeAccent,
            ) {
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

    override fun onResume() {
        super.onResume()
        // Startup-Safe-Mode (Crash-Schleife): Die Application hat beim
        // Start entschieden, dass dieser Versuch die volle UI nicht
        // versuchen soll - sofort in die Diagnose-Activity weiterleiten.
        // (RESUMED heisst: Die UI WAR erreicht - danach Reset des
        // Crash-Schleifen-Zaehlers, siehe CrashLoopPolicy.onUiReached.)
        if (CrashSafeModeState.active) {
            startActivity(android.content.Intent(this, CrashDiagnosticsActivity::class.java))
            finish()
            return
        }
        crashLoopGuard.markUiReached()
    }
}

/**
 * WindowWidthSizeClass OHNE M3-calculateWindowSizeClass: M3 laesst androidx.window
 * intern die OEM-Vendor-Klassen (`androidx.window.extensions.*` bzw.
 * `androidx.window.sidecar.*`) aufloesen. Fehlen diese auf dem Geraet (z.B.
 * S23/OneUI, API-34-google_apis-Emulator ohne Window-Extensions-Provider), wirft
 * die Aufloesung beim Activity-Start `NoClassDefFoundError` (LinkageError) — die
 * App startete dann auf API 34-Geraeten sofort nicht mehr. Der Compose-Compiler
 * verbietet ausserdem try/catch um Composable-Aufrufe, die M3-Funktion ist also
 * nicht absicherbar — und unnötig: Ohne Vendor-Provider liefert sie ohnehin nur
 * die reine Framework-Metrik (WindowMetricsCalculatorCompat → currentWindowMetrics).
 * Deshalb wird die Breite direkt aus den Framework-Metriken abgeleitet — identische
 * Schwellwerte wie M3 (600/840 dp).
 */
@Suppress("DEPRECATION")
private fun resolveSafeWindowWidthSizeClass(activity: Activity): WindowWidthSizeClass {
    val widthDp = if (Build.VERSION.SDK_INT >= 30) {
        activity.windowManager.currentWindowMetrics.bounds.width() /
            activity.resources.displayMetrics.density
    } else {
        activity.resources.configuration.screenWidthDp.toFloat()
    }
    return when {
        widthDp >= 840f -> WindowWidthSizeClass.Expanded
        widthDp >= 600f -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Compact
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
        composable("replay_library") {
            ReplayLibraryScreen(navController)
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
            val twitchViewModel: TwitchChannelViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val twitchState by twitchViewModel.uiState.collectAsState()
            SettingsStreamingObsScreen(
                uiState = uiState,
                viewModel = viewModel,
                twitchViewModel = twitchViewModel,
                twitchState = twitchState,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings_camera") {
            SettingsCameraScreen()
        }
        composable("settings_appearance") {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            SettingsAppearanceScreen(
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
        composable("settings_logs") {
            SettingsLogsScreen(
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
        composable("help_route") {
            HelpScreen(navController = navController)
        }
    }
}
