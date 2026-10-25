package com.vivid.irlbroadcaster

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val ORIENTATION_FIXTURE_PREFIX = "orientation-"

/** Opt-in local MP4 proof of the shared stream encoder's pixel axes. Never connects to an ingest. */
@RunWith(AndroidJUnit4::class)
class CameraOutputOrientationInstrumentedTest {
    @Test fun previewAndEncodedCameraUseTheSelectedOrientation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("runCameraOutputOrientation") == "true")
        val fps = (arguments.getString("fps") ?: "30").toInt()
        require(fps in listOf(30, 60))
        val evidence = File(instrumentation.targetContext.filesDir, "camera-output-orientation").apply { mkdirs() }
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
            assertTrue("Do not interrupt a live stream or recording", engine.canChangeCameraMode())
            recoverInterruptedFixtures(scenes, settings)
            val original = runBlocking { settings.appSettingsFlow.first() }
            assertFalse("Do not activate a configured chat bot", original.chatBotEnabled)
            val active = runBlocking { scenes.activeSceneIdFlow.first() }
            val previous = runBlocking { scenes.scenesFlow.first().find { it.id == active } }
            val replayDirectory = File(instrumentation.targetContext.filesDir, "replays")
            require(replayDirectory.listFiles { file -> file.extension == "mp4" }.orEmpty().size < ReplayStorage.DEFAULT_MAX_FILES) {
                "Refuse to prune existing user recordings"
            }
            val createdFiles = mutableListOf<File>()
            val prefix = "orientation-${System.currentTimeMillis()}"
            val widget = StreamWidget("$prefix-label", "Orientation probe", WidgetType.TEXT,
                configuration = WidgetConfiguration(widgetTemplate = "UP {time}", widgetShowTime = false,
                    widgetShowLocation = false, widgetShowSpeed = false))
            val markerFile = File(evidence, "marker.png")
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(android.graphics.Color.MAGENTA)
                markerFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            val marker = StreamWidget("$prefix-marker", "Orientation marker", WidgetType.IMAGE,
                configuration = WidgetConfiguration(imageWidgetUri = markerFile.toURI().toString(),
                    imageWidgetSizeDp = 48, imageWidgetOpacity = 1f))
            val scene = StreamScene("$prefix-scene", "Orientation probe", videoSource = SceneVideoSource.CAMERA,
                widgets = listOf(SceneWidget(widget.id, x = 50f, y = 0f), SceneWidget(marker.id, x = 50f, y = 25f)))
            try {
                runBlocking { scenes.saveWidget(widget); scenes.saveWidget(marker); scenes.saveScene(scene) }
                for (orientation in AppOrientation.entries) {
                    runBlocking { settings.updateAppOrientation(orientation) }
                    Thread.sleep(2_000)
                    val configuration = original.copy(appOrientation = orientation,
                        encoderPreset = if (fps == 60) EncoderPreset.FHD60 else EncoderPreset.HD30,
                        videoCodecPreference = VideoCodecPreference.H264, videoFps = fps,
                        cameraMode = if (fps == 60) CameraMode.EXPERIMENTAL_60_FPS else CameraMode.STANDARD)
                    scenario.onActivity {
                        runBlocking { controller.applyScene(scene) }
                        engine.configureCameraMode(configuration.cameraMode)
                        engine.configureStreamVideo(configuration)
                        engine.configureEncoder(ResolvedEncoderConfig(VideoCodecPreference.H264,
                            configuration.encoderPreset, false, fps), true)
                    }
                    Thread.sleep(1_000)
                    screenshot(evidence, "${orientation.name}-$fps-idle.png")
                    scenario.onActivity { assertTrue("Start local recording", engine.startReplay(includeAudio = false)) }
                    await { engine.replayState.value is ReplayState.Recording }
                    val file = (engine.replayState.value as ReplayState.Recording).file
                    createdFiles += file
                    Thread.sleep(4_000)
                    screenshot(evidence, "${orientation.name}-$fps-recording.png")
                    val applied = requireNotNull(engine.activeEncoder.value)
                    val cameraMode = engine.cameraModeStatus.value
                    val measuredFps = engine.measuredEncoderFps.value
                    scenario.onActivity { engine.stopReplay() }
                    assertTrue(file.length() > 0L)
                    val format = assertEncodedFrame(file, evidence, orientation, fps, applied.preset)
                    File(evidence, "${orientation.name}-$fps-metrics.txt").writeText(
                        "orientation=$orientation\nformat=$format\napplied=$applied\nmode=$cameraMode\nmeasuredFps=$measuredFps\n")
                    file.copyTo(File(evidence, "${orientation.name}-$fps.mp4"), overwrite = true)
                    file.delete()
                }
            } finally {
                scenario.onActivity { engine.stopReplay() }
                createdFiles.forEach { it.delete() }
                runBlocking {
                    settings.updateAppOrientation(original.appOrientation)
                    scenes.deleteScene(scene.id); scenes.deleteWidget(widget.id); scenes.deleteWidget(marker.id)
                }
                scenario.onActivity {
                    engine.configureCameraMode(original.cameraMode)
                    engine.configureStreamVideo(original)
                    engine.configureEncoder(ResolvedEncoderConfig(original.videoCodecPreference,
                        original.encoderPreset, false, original.videoFps ?: original.encoderPreset.fps), original.encoderAutoFallback)
                    if (previous != null) runBlocking { controller.applyScene(previous) }
                    else { engine.setSceneEmpty(false); runBlocking { scenes.setActiveScene(active) } }
                }
            }
        }
    }

    private fun recoverInterruptedFixtures(scenes: SceneRepository, settings: SettingsRepository) {
        runBlocking {
            val all = scenes.scenesFlow.first()
            val current = scenes.activeSceneIdFlow.first()
            if (current?.startsWith(ORIENTATION_FIXTURE_PREFIX) == true) {
                val restored = all.firstOrNull { !it.id.startsWith(ORIENTATION_FIXTURE_PREFIX) && it.enabled }
                scenes.setActiveScene(restored?.id)
                settings.updateAppOrientation(AppOrientation.PORTRAIT)
            }
            all.filter { it.id.startsWith(ORIENTATION_FIXTURE_PREFIX) }.forEach { scenes.deleteScene(it.id) }
            scenes.widgetsFlow.first().filter { it.id.startsWith(ORIENTATION_FIXTURE_PREFIX) }.forEach { scenes.deleteWidget(it.id) }
        }
    }

    private fun assertEncodedFrame(
        file: File, evidence: File, orientation: AppOrientation, fps: Int, preset: EncoderPreset,
    ): MediaFormat {
        val extractor = MediaExtractor()
        val format = try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).map(extractor::getTrackFormat)
                .first { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
        } finally { extractor.release() }
        val expectedWidth = if (orientation == AppOrientation.PORTRAIT) preset.height else preset.width
        val expectedHeight = if (orientation == AppOrientation.PORTRAIT) preset.width else preset.height
        assertEquals("Encoded width", expectedWidth, format.getInteger(MediaFormat.KEY_WIDTH))
        assertEquals("Encoded height", expectedHeight, format.getInteger(MediaFormat.KEY_HEIGHT))
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val frame = requireNotNull(retriever.getFrameAtTime(2_000_000))
            File(evidence, "${orientation.name}-$fps-frame.png").outputStream().use {
                frame.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            assertEquals(expectedWidth, frame.width)
            assertEquals(expectedHeight, frame.height)
            val markerY = expectedHeight / 4 + minOf(expectedWidth, expectedHeight) / 30
            val color = frame.getPixel(expectedWidth / 2, markerY)
            assertTrue("Scene marker must remain visible after encoder restart: $color",
                android.graphics.Color.red(color) > 200 && android.graphics.Color.blue(color) > 200 &&
                    android.graphics.Color.green(color) < 80)
            frame.recycle()
        } finally { retriever.release() }
        return format
    }

    private fun screenshot(directory: File, name: String) {
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue("Recording did not start", condition())
    }
}
