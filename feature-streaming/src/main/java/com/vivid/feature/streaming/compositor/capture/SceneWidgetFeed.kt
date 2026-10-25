package com.vivid.feature.streaming.compositor.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

/** Text/clock content updates independently of camera frame rate and shares one bitmap per source. */
class SceneWidgetFeed(private val publish: (String, Bitmap) -> Boolean) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sources = emptyList<SceneSource>()
    private var generation = 0L
    private var job: Job? = null

    @Synchronized
    fun update(next: List<SceneSource>) {
        val automatic = next.filter { it.type == SceneSourceType.WIDGET &&
            (it.locator == "clock" || it.locator.startsWith("text:")) }
            .map { it.copy(name = "") }
        if (sources == automatic) return
        sources = automatic
        val current = ++generation
        job?.cancel()
        job = if (automatic.isEmpty()) null else scope.launch {
            val previous = mutableMapOf<String, String>()
            do {
                automatic.forEach { source ->
                    val text = content(source, Instant.now(), ZoneId.systemDefault()) ?: return@forEach
                    if (previous[source.id] != text) {
                        val bitmap = render(text)
                        try {
                            ensureActive()
                            synchronized(this@SceneWidgetFeed) {
                                if (generation == current && publish(source.id, bitmap)) previous[source.id] = text
                            }
                        } finally { bitmap.recycle() }
                    }
                }
                if (automatic.none { it.locator == "clock" }) break
                delay(1_000L - System.currentTimeMillis().mod(1_000L))
            } while (isActive)
        }
    }

    companion object {
        fun content(source: SceneSource, instant: Instant, zone: ZoneId): String? = when {
            source.type != SceneSourceType.WIDGET -> null
            source.locator == "clock" -> DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone).format(instant)
            source.locator.startsWith("text:") -> source.locator.removePrefix("text:").take(2048)
            else -> null
        }

        internal fun render(text: String): Bitmap {
            val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setShadowLayer(3f, 1f, 1f, Color.BLACK)
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 608)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(6)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).setIncludePad(true).build()
            return Bitmap.createBitmap(640, (layout.height + 32).coerceIn(96, 512), Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                canvas.drawColor(Color.argb(160, 0, 0, 0))
                canvas.translate(16f, 16f)
                layout.draw(canvas)
            }
        }
    }
}
