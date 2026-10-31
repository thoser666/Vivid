package com.vivid.irlbroadcaster

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vivid.core.data.*
import com.vivid.feature.streaming.*
import com.vivid.feature.streaming.scene.SceneController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in, loopback RTMP acceptance. Dedicated FOSS installation; restores settings and fixtures. */
@RunWith(AndroidJUnit4::class)
class SceneLayerStreamInstrumentedTest {
    @get:Rule
    val permissions = EmulatorTestPermissions.rule()

    @Test fun sceneLayerSurvivesNavigationBackgroundAndEffects() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runSceneLayerStream") == "true")
        val options = streamProbeOptions(args)
        val (port, fps, fullHd, steadySeconds, orientation) = options
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val evidence = File(context.filesDir, "scene-layer-stream").apply { mkdirs() }
        val prefix = "layer-test-${System.currentTimeMillis()}"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var engine: StreamingEngine
            lateinit var settings: SettingsRepository
            lateinit var scenes: SceneRepository
            lateinit var controller: SceneController
            scenario.onActivity { activity ->
                settings = activity.settingsRepository
                val vm = ViewModelProvider(activity)[StreamingViewModel::class.java]
                engine = vm.streamingEngine
                fun field(name: String) = vm.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(vm)
                scenes = field("sceneRepository") as SceneRepository
                controller = field("sceneController") as SceneController
            }
            assertTrue("Never interrupt a live stream", engine.canChangeCameraMode())
            val original = runBlocking { settings.appSettingsFlow.first() }
            assertFalse("Acceptance must not activate a configured bot", original.chatBotEnabled)
            val active = runBlocking { scenes.activeSceneIdFlow.first() }
            val previous = runBlocking { scenes.scenesFlow.first().find { it.id == active } }
            val widgets = listOf(
                StreamWidget("$prefix-text", "Clock", WidgetType.TEXT, configuration = WidgetConfiguration(
                    widgetTemplate = "LIVE {time}", widgetShowLocation = false, widgetShowSpeed = false)),
                StreamWidget("$prefix-browser", "Browser", WidgetType.BROWSER, configuration = WidgetConfiguration(
                    browserUrl = "https://example.com", browserWidth = 320, browserHeight = 180)),
            )
            val scene = StreamScene("$prefix-scene", "Acceptance", videoSource = SceneVideoSource.EMPTY,
                widgets = listOf(SceneWidget(widgets[0].id, x = 100f, y = 100f), SceneWidget(widgets[1].id, x = 0f, y = 0f)))
            val cameraScene = scene.copy(id = "$prefix-camera", videoSource = SceneVideoSource.CAMERA)
            val empty = scene.copy(id = "$prefix-empty", widgets = emptyList())
            val events = mutableListOf<String>()
            var started = 0L
            var cpuStarted = 0L
            fun mark(label: String) { events += "${SystemClock.elapsedRealtime() - started},$label" }
            try {
                runBlocking {
                    scenes.scenesFlow.first().filter { it.id.startsWith("layer-test-") }.forEach { scenes.deleteScene(it.id) }
                    scenes.widgetsFlow.first().filter { it.id.startsWith("layer-test-") }.forEach { scenes.deleteWidget(it.id) }
                    settings.updateAppOrientation(orientation)
                    widgets.forEach { scenes.saveWidget(it) }
                    listOf(scene, cameraScene, empty).forEach { scenes.saveScene(it) }
                }
                Thread.sleep(2_000)
                scenario.onActivity {
                    configureProbeCamera(engine, fps, fullHd)
                    runBlocking { controller.applyScene(scene) }
                    StreamingService.start(it, listOf("rtmp://127.0.0.1:$port/live/audit"))
                }
                await { engine.streamingState.value is StreamingState.Streaming }
                started = SystemClock.elapsedRealtime()
                cpuStarted = android.os.Process.getElapsedCpuTime()
                mark("started")
                await { var found = false; scenario.onActivity { found = browser(engine) != null }; found }
                Thread.sleep(5_000)
                val script = """document.body.style.cssText='margin:0;background:transparent';document.body.innerHTML='<div id="probe" style="width:320px;height:180px;background:#ff00ff;color:black;font:32px sans-serif">BROWSER</div>';window.probeTicks=0;setInterval(()=>{window.probeTicks++;document.getElementById('probe').style.background=(window.probeTicks%2)?'#00ffff':'#ff00ff';document.getElementById('probe').textContent='BROWSER '+window.probeTicks},500);'ready'"""
                scenario.onActivity { requireNotNull(browser(engine)).evaluateJavascript(script, null) }
                val websocketScript = """window.wssReceived=0;window.wssState='connecting';window.probeSocket=new WebSocket('wss://ws.postman-echo.com/raw');window.probeSocket.onopen=()=>{window.wssState='open';window.probeSocket.send('vivid-probe-0')};window.probeSocket.onmessage=e=>{if(String(e.data).startsWith('vivid-probe-')){window.wssReceived++;document.getElementById('probe').textContent='WSS '+window.wssReceived;document.getElementById('probe').style.background=(window.wssReceived%2)?'#00ffff':'#ff00ff'}};window.probeSocket.onerror=()=>{window.wssState='error'};setInterval(()=>{if(window.probeSocket.readyState===1)window.probeSocket.send('vivid-probe-'+window.probeTicks)},500);'started'"""
                scenario.onActivity { requireNotNull(browser(engine)).evaluateJavascript(websocketScript, null) }
                mark("browser_animation_wss")
                if (steadySeconds > 0) {
                    mark("steady_start")
                    Thread.sleep(steadySeconds * 1_000L)
                    mark("steady_end")
                }
                Thread.sleep(4_000)
                screenshot(evidence, "${orientation.name}-$fps-preview.png")
                scenario.onActivity { engine.setVideoFilter(VideoFilter.GRAYSCALE) }
                mark("grayscale")
                Thread.sleep(2_000)
                var settingsDescription = ""
                scenario.onActivity {
                    settingsDescription = it.getString(com.vivid.feature.streaming.R.string.streaming_settings_content_desc)
                }
                val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
                val settingsButton = findDescription(root, settingsDescription)
                val clickable = generateSequence(requireNotNull(settingsButton)) { it.parent }.first { it.isClickable }
                assertTrue("Open real settings navigation", clickable.performAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                Thread.sleep(2_000)
                screenshot(evidence, "${orientation.name}-$fps-settings.png")
                mark("settings_open")
                runBlocking { scenes.saveWidget(widgets[0].copy(configuration = widgets[0].configuration.copy(widgetTemplate = "SETTINGS {time}"))) }
                Thread.sleep(3_000)
                // Removing the preview navigation owner must not retire the scene composition.
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                mark("activity_stopped")
                Thread.sleep(6_000)
                val changed = widgets[0].copy(configuration = widgets[0].configuration.copy(widgetTemplate = "BACKGROUND {time}"))
                runBlocking { scenes.saveWidget(changed) }
                mark("background_edit")
                Thread.sleep(6_000)
                val (ticks, wsResult) = assertBackgroundBrowser(engine)
                mark("browser_ticks_$ticks")
                mark("wss_$wsResult")
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                Thread.sleep(1_000)
                // Frequent effect rebuilds while empty must not remove the source gate.
                scenario.onActivity { runBlocking { controller.applyScene(empty) } }
                mark("empty_no_widgets")
                rebuildEffectsOnEmptySource(scenario, engine)
                Thread.sleep(2_000)
                mark("camera")
                scenario.onActivity { runBlocking { controller.applyScene(cameraScene) } }
                Thread.sleep(2_000)
                scenario.onActivity { runBlocking { controller.applyScene(empty) } }
                mark("empty_after_camera")
                Thread.sleep(3_000)
                scenario.onActivity { runBlocking { controller.applyScene(scene) } }
                mark("scene_restored")
                Thread.sleep(3_000)
                val memory = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
                File(evidence, "${orientation.name}-$fps-metrics.txt").writeText(
                    "requestedFps=$fps\nappliedMode=${engine.cameraModeStatus.value}\nencoder=${engine.activeEncoder.value}\nmeasuredFps=${engine.measuredEncoderFps.value}\nbrowserTicks=$ticks\nwss=$wsResult\nelapsedMs=${SystemClock.elapsedRealtime() - started}\ncpuMs=${android.os.Process.getElapsedCpuTime() - cpuStarted}\npssKb=${memory.totalPss}\n")
            } finally {
                scenario.onActivity { StreamingService.stop(it); engine.stopStream(); engine.resetVideoFilter(); engine.resetLowLightBoost() }
                runBlocking {
                    listOf(scene, cameraScene, empty).forEach { scenes.deleteScene(it.id) }
                    widgets.forEach { scenes.deleteWidget(it.id) }
                    settings.updateAppOrientation(original.appOrientation)
                }
                scenario.onActivity {
                    engine.configureCameraMode(original.cameraMode)
                    if (previous != null) runBlocking { controller.applyScene(previous) }
                    else { engine.setSceneEmpty(false); runBlocking { scenes.setActiveScene(active) } }
                }
                File(evidence, "${orientation.name}-$fps-events.csv").writeText("elapsedMs,event\n" + events.joinToString("\n"))
            }
        }
    }

    @Test fun controlsStayAboveOpaqueBrowserInBothOrientations() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runSceneControls") == "true")
        val port = requireNotNull(args.getString("localRtmpPort")).toInt()
        require(port in 1024..65534)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val evidence = File(instrumentation.targetContext.filesDir, "scene-layer-controls").apply { mkdirs() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var engine: StreamingEngine
            lateinit var settings: SettingsRepository
            lateinit var scenes: SceneRepository
            lateinit var controller: SceneController
            scenario.onActivity {
                settings = it.settingsRepository
                val vm = ViewModelProvider(it)[StreamingViewModel::class.java]
                engine = vm.streamingEngine
                fun field(name: String) = vm.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(vm)
                scenes = field("sceneRepository") as SceneRepository
                controller = field("sceneController") as SceneController
            }
            assertTrue(engine.canChangeCameraMode())
            val original = runBlocking { settings.appSettingsFlow.first() }
            assertFalse(original.chatBotEnabled)
            val active = runBlocking { scenes.activeSceneIdFlow.first() }
            val previous = runBlocking { scenes.scenesFlow.first().find { it.id == active } }
            val prefix = "controls-test-${System.currentTimeMillis()}"
            val widget = StreamWidget("$prefix-widget", "Opaque browser", WidgetType.BROWSER,
                configuration = WidgetConfiguration(browserUrl = "https://example.com", browserWidth = 1280, browserHeight = 720))
            val scene = StreamScene("$prefix-scene", "Controls probe", videoSource = SceneVideoSource.EMPTY,
                widgets = listOf(SceneWidget(widget.id, x = 0f, y = 0f)))
            val labels = mutableMapOf<Int, String>()
            val ids = listOf(com.vivid.feature.streaming.R.string.streaming_start,
                com.vivid.feature.streaming.R.string.streaming_stop, com.vivid.feature.streaming.R.string.streaming_controls,
                com.vivid.feature.streaming.R.string.streaming_source_camera, com.vivid.feature.streaming.R.string.streaming_settings_content_desc,
                com.vivid.feature.streaming.R.string.streaming_stop_confirm_title, com.vivid.feature.streaming.R.string.streaming_stop_confirm)
            fun root() = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
            fun tapText(id: Int) = tapNode(requireNotNull(findText(root(), labels.getValue(id))))
            try {
                runBlocking { scenes.saveWidget(widget); scenes.saveScene(scene) }
                scenario.onActivity { runBlocking { controller.applyScene(scene) } }
                for ((index, orientation) in AppOrientation.entries.withIndex()) {
                    val profile = StreamProfile("$prefix-profile", "Local controls probe", streamUrl = "rtmp://127.0.0.1:${port + index}/live/audit",
                        encoderPreset = EncoderPreset.HD30, videoCodecPreference = VideoCodecPreference.H264, videoFps = 30)
                    runBlocking {
                        settings.updateStreamProfiles(original.streamProfiles + profile, profile.id)
                        settings.updateAppOrientation(orientation)
                    }
                    scenario.recreate()
                    Thread.sleep(2_000)
                    scenario.onActivity { activity -> ids.forEach { labels[it] = activity.getString(it) } }
                    await { var ready = false; scenario.onActivity { ready = browser(engine) != null }; ready }
                    Thread.sleep(3_000)
                    val opaque = "document.documentElement.style.background='#ff00ff';document.body.style.cssText='margin:0;background:#ff00ff';document.body.innerHTML='<div style=\"width:100vw;height:100vh;background:#ff00ff\">OPAQUE WIDGET</div>';window.widgetTouches=0;document.addEventListener('pointerdown',()=>window.widgetTouches++);'opaque'"
                    scenario.onActivity { requireNotNull(browser(engine)).evaluateJavascript(opaque, null) }
                    Thread.sleep(500)
                    screenshot(evidence, "${orientation.name}-idle.png")
                    // Real touch injection exercises hit testing, not the accessibility click action.
                    tapText(com.vivid.feature.streaming.R.string.streaming_start)
                    await { engine.streamingState.value is StreamingState.Streaming }
                    Thread.sleep(1_000)
                    scenario.onActivity { requireNotNull(browser(engine)).evaluateJavascript(opaque, null) }
                    screenshot(evidence, "${orientation.name}-live.png")
                    tapText(com.vivid.feature.streaming.R.string.streaming_controls)
                    await { findText(root(), labels.getValue(com.vivid.feature.streaming.R.string.streaming_source_camera)) != null }
                    screenshot(evidence, "${orientation.name}-controls.png")
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    Thread.sleep(500)
                    tapNode(requireNotNull(findDescription(root(), labels.getValue(com.vivid.feature.streaming.R.string.streaming_settings_content_desc))))
                    await { findDescription(root(), labels.getValue(com.vivid.feature.streaming.R.string.streaming_settings_content_desc)) == null }
                    screenshot(evidence, "${orientation.name}-settings.png")
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    await { findDescription(root(), labels.getValue(com.vivid.feature.streaming.R.string.streaming_settings_content_desc)) != null }
                    tapText(com.vivid.feature.streaming.R.string.streaming_stop)
                    await { findText(root(), labels.getValue(com.vivid.feature.streaming.R.string.streaming_stop_confirm_title)) != null }
                    screenshot(evidence, "${orientation.name}-stop-confirm.png")
                    tapText(com.vivid.feature.streaming.R.string.streaming_stop_confirm)
                    await { engine.streamingState.value is StreamingState.Idle }
                    screenshot(evidence, "${orientation.name}-stopped.png")
                }
            } finally {
                scenario.onActivity { StreamingService.stop(it); engine.stopStream() }
                runBlocking {
                    settings.updateStreamProfiles(original.streamProfiles, original.activeStreamId)
                    settings.updateAppOrientation(original.appOrientation)
                    scenes.deleteScene(scene.id); scenes.deleteWidget(widget.id)
                }
                scenario.onActivity {
                    if (previous != null) runBlocking { controller.applyScene(previous) }
                    else { engine.setSceneEmpty(false); runBlocking { scenes.setActiveScene(active) } }
                }
            }
        }
    }

    private fun rebuildEffectsOnEmptySource(scenario: ActivityScenario<MainActivity>, engine: StreamingEngine) {
        repeat(12) { index ->
            scenario.onActivity {
                engine.setVideoFilter(if (index % 2 == 0) VideoFilter.SEPIA else VideoFilter.NONE)
                engine.toggleLowLightBoost()
            }
            Thread.sleep(100)
        }
    }

    private data class StreamProbeOptions(
        val port: Int, val fps: Int, val fullHd: Boolean, val steadySeconds: Int, val orientation: AppOrientation,
    )

    private fun streamProbeOptions(args: android.os.Bundle): StreamProbeOptions {
        val port = requireNotNull(args.getString("localRtmpPort")).toInt()
        require(port in 1024..65535)
        val fps = (args.getString("fps") ?: "30").toInt()
        require(fps == 30 || fps == 60)
        val fullHd = args.getString("fullHd") == "true"
        val steadySeconds = (args.getString("steadySeconds") ?: "0").toInt()
        require(steadySeconds in 0..600)
        val orientation = AppOrientation.valueOf(args.getString("orientation") ?: "LANDSCAPE")
        return StreamProbeOptions(port, fps, fullHd, steadySeconds, orientation)
    }

    private fun configureProbeCamera(engine: StreamingEngine, fps: Int, fullHd: Boolean) {
        engine.configureCameraMode(if (fps == 60) CameraMode.EXPERIMENTAL_60_FPS else CameraMode.STANDARD)
        if (fps == 60) engine.configureEncoder(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD60, false), false)
        else if (fullHd) engine.configureEncoder(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, false), false)
    }

    private fun assertBackgroundBrowser(engine: StreamingEngine): Pair<String, String> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val latch = CountDownLatch(1)
        var ticks = ""
        instrumentation.runOnMainSync {
            requireNotNull(browser(engine)).evaluateJavascript("window.probeTicks") { ticks = it; latch.countDown() }
        }
        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue("Browser JavaScript must keep ticking in background: $ticks", (ticks.toIntOrNull() ?: 0) >= 10)
        val wsLatch = CountDownLatch(1)
        var wsResult = ""
        instrumentation.runOnMainSync {
            requireNotNull(browser(engine)).evaluateJavascript("window.wssState+':'+window.wssReceived") { wsResult = it; wsLatch.countDown() }
        }
        assertTrue(wsLatch.await(3, TimeUnit.SECONDS))
        assertTrue("Live WSS echo messages must update the background widget: $wsResult", wsResult.contains("open:") &&
            (wsResult.trim('"').substringAfter(':').toIntOrNull() ?: 0) >= 10)
        return ticks to wsResult
    }

    private fun findText(node: android.view.accessibility.AccessibilityNodeInfo, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.text?.toString() == text) return node
        return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let { child -> findText(child, text) } }
    }

    private fun tapNode(node: android.view.accessibility.AccessibilityNodeInfo) {
        val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
        val now = SystemClock.uptimeMillis()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for ((action, time) in listOf(android.view.MotionEvent.ACTION_DOWN to now, android.view.MotionEvent.ACTION_UP to now + 50)) {
            val event = android.view.MotionEvent.obtain(now, time, action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }

    private fun findDescription(node: android.view.accessibility.AccessibilityNodeInfo, description: String): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.contentDescription?.toString() == description) return node
        return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let { child -> findDescription(child, description) } }
    }

    private fun browser(engine: StreamingEngine): WebView? {
        val host = engine.sceneLayerHost
        val sessions = host.javaClass.getDeclaredField("sessions").apply { isAccessible = true }.get(host) as Map<*, *>
        return sessions.values.firstNotNullOfOrNull { session ->
            val view = session!!.javaClass.getDeclaredField("view").apply { isAccessible = true }.get(session) as? View
            view?.let(::findBrowser)
        }
    }
    private fun findBrowser(view: View): WebView? = if (view is WebView) view else
        (view as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { findBrowser(group.getChildAt(it)) } }
    private fun screenshot(directory: File, name: String) {
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue("Scene stream transition timed out", condition())
    }
}
