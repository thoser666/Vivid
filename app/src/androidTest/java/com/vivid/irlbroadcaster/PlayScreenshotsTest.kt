package com.vivid.irlbroadcaster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.vivid.feature.streaming.R as StreamingR
import com.vivid.feature.streaming.ui.PreviewFrameOverride
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * Erzeugt die zwei Play-Store-Screenshots per UI-Test (fastlane screengrab,
 * Lane `capture_play_screenshots` im Fastfile):
 *
 *   `1_live_stream` → Live-Stream-Hauptscreen (Start-Destination)
 *   `2_settings`    → Einstellungen (Top-Bar-Zahnrad, content-desc „Open Settings“)
 *
 * Die Lane übersetzt die Namen in die supply-konformen Dateinamen
 * (`1_en-US.png`, `2_en-US.png`) unter `fastlane/metadata/android/images/phoneScreenshots/`.
 *
 * Wichtig für Compose: `UiAutomatorScreenshotStrategy` ist nötig, sonst
 * liefert screengrab schwarze Screenshots (bekanntes Compose-Problem).
 *
 * Die Emulator-Kamera liefert nur ein Testmuster aus farbigen Blöcken — der
 * erste veröffentlichte Entwurf zeigte genau das. Deshalb setzt der Test über
 * [PreviewFrameOverride] ein Standbild als Vorschau (siehe [mockPreviewFrame]).
 */
class PlayScreenshotsTest {

    // Grants first: the compose rule launches MainActivity when applied (#249).
    // The locale rule stays separate — screengrab re-instruments per locale.
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Init statt @Before: die Rule startet MainActivity beim Anwenden, also
    // bevor @Before läuft — ein dort gesetzter Frame käme für die erste
    // Komposition der Vorschau zu spät.
    init {
        PreviewFrameOverride.frame = mockPreviewFrame()
    }

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(EmulatorTestPermissions.rule())
        .around(composeRule)

    // LocaleTestRule: screengrab instrumentiert pro Locale neu — die Regel
    // stellt die Gerätesprache auf das Test-Locale (en-US) und zurück.
    @Rule
    @JvmField
    val localeTestRule = LocaleTestRule()

    @Before
    fun setUp() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
    }

    @After
    fun tearDown() {
        // Produktionspfad wiederherstellen: ohne gesetzten Frame rendert die
        // Vorschau wie im echten Betrieb über die Kamera.
        PreviewFrameOverride.frame = null
    }

    @Test
    fun captureLiveStream() {
        composeRule.waitForIdle()
        Screengrab.screenshot("1_live_stream")
    }

    @Test
    fun captureSettings() {
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(StreamingR.string.streaming_settings_content_desc),
        ).performClick()
        composeRule.waitForIdle()
        Screengrab.screenshot("2_settings")
    }

    /**
     * Ruhige Abendlandschaft als Vorschau-Ersatz: Dämmerungshimmel, Sonne über
     * einer Hügelkette, dunkler Vordergrund. Deterministisch und ohne Assets,
     * damit die Store-Screenshots reproduzierbar bleiben; 1080×1920 entspricht
     * der Geometrie der bisherigen Bilddateien (ContentScale.Crop schneidet
     * andere Gerätegrößen passend zu).
     */
    private fun mockPreviewFrame(width: Int = 1080, height: Int = 1920): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(
                    0xFF0B1B2E.toInt(),
                    0xFF1D4B6B.toInt(),
                    0xFFB8723F.toInt(),
                    0xFF241A15.toInt(),
                ),
                floatArrayOf(0f, 0.42f, 0.68f, 1f),
                Shader.TileMode.CLAMP,
            )
        })
        canvas.drawCircle(w * 0.63f, h * 0.66f, w * 0.075f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD9A3.toInt()
        })

        val ridge = Path().apply {
            moveTo(0f, h * 0.72f)
            lineTo(w * 0.22f, h * 0.64f)
            lineTo(w * 0.42f, h * 0.70f)
            lineTo(w * 0.64f, h * 0.60f)
            lineTo(w * 0.82f, h * 0.68f)
            lineTo(w, h * 0.62f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        canvas.drawPath(ridge, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF13212B.toInt() })

        val foreground = Path().apply {
            moveTo(0f, h * 0.86f)
            lineTo(w * 0.3f, h * 0.83f)
            lineTo(w * 0.55f, h * 0.85f)
            lineTo(w * 0.8f, h * 0.82f)
            lineTo(w, h * 0.87f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        canvas.drawPath(foreground, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A141A.toInt() })

        return bitmap
    }
}
