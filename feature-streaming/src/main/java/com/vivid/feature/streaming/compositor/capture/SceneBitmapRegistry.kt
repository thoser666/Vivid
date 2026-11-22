package com.vivid.feature.streaming.compositor.capture

import android.graphics.Bitmap
import java.io.Closeable
import com.vivid.core.data.SceneComposition

/** Widget producers publish only when content changes; camera frames never pass through bitmaps. */
class SceneBitmapRegistry : Closeable {
    private data class Entry(val revision: Long, val bitmap: Bitmap)
    private val entries = mutableMapOf<String, Entry>()
    private var revision = 0L

    /** Copies ownership once per content update; callers retain ownership of their bitmap. */
    @Synchronized
    fun publish(sourceId: String, bitmap: Bitmap) {
        require(sourceId.isNotBlank() && !bitmap.isRecycled)
        require(bitmap.width <= MAX_BITMAP_SIZE && bitmap.height <= MAX_BITMAP_SIZE)
        require(sourceId in entries || entries.size < SceneComposition.MAX_SOURCES)
        val previousPixels = entries[sourceId]?.bitmap?.let { it.width.toLong() * it.height } ?: 0L
        val pixels = entries.values.sumOf { it.bitmap.width.toLong() * it.bitmap.height }
        require(pixels - previousPixels + bitmap.width.toLong() * bitmap.height <= MAX_TOTAL_PIXELS)
        val owned = requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
        entries.put(sourceId, Entry(++revision, owned))?.bitmap?.recycle()
    }

    /** Consumer must upload synchronously; the registry protects this bitmap from recycling. */
    @Synchronized
    fun consumeChanged(sourceId: String, previousRevision: Long,
        consume: (revision: Long, bitmap: Bitmap) -> Unit) {
        val entry = entries[sourceId] ?: return
        if (entry.revision != previousRevision) consume(entry.revision, entry.bitmap)
    }

    @Synchronized
    fun remove(sourceId: String) { entries.remove(sourceId)?.bitmap?.recycle() }

    @Synchronized
    fun retainSources(sourceIds: Set<String>) {
        entries.keys.filter { it !in sourceIds }.forEach(::remove)
    }

    @Synchronized
    override fun close() { entries.values.forEach { it.bitmap.recycle() }; entries.clear() }

    companion object {
        const val MAX_BITMAP_SIZE = 2048
        const val MAX_TOTAL_PIXELS = 8_388_608L
    }
}
