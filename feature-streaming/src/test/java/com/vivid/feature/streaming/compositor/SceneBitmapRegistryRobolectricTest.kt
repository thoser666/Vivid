package com.vivid.feature.streaming.compositor

import android.graphics.Bitmap
import android.graphics.Color
import com.vivid.feature.streaming.compositor.capture.SceneBitmapRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SceneBitmapRegistryRobolectricTest {
    @Test fun `widget updates own a copy and are consumed only once per revision`() {
        val registry = SceneBitmapRegistry()
        val original = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        registry.publish("widget", original)
        original.recycle()
        var revision = 0L
        var owned: Bitmap? = null
        registry.consumeChanged("widget", revision) { next, bitmap ->
            revision = next
            owned = bitmap
            assertFalse(bitmap.isRecycled)
            assertEquals(Color.GREEN, bitmap.getPixel(0, 0))
        }
        assertTrue(revision > 0)
        registry.consumeChanged("widget", revision) { _, _ -> fail("Unchanged widget uploaded again") }
        registry.close()
        assertTrue(owned!!.isRecycled)
    }

    @Test fun `replacing and removing scenes release only the registry owned bitmaps`() {
        val registry = SceneBitmapRegistry()
        val original = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        registry.publish("old", original)
        var old: Bitmap? = null
        registry.consumeChanged("old", 0) { _, bitmap -> old = bitmap }
        registry.publish("old", original)
        assertTrue(old!!.isRecycled)
        var current: Bitmap? = null
        registry.consumeChanged("old", 0) { _, bitmap -> current = bitmap }
        registry.retainSources(emptySet())
        assertTrue(current!!.isRecycled)
        assertFalse(original.isRecycled)
        registry.consumeChanged("old", 0) { _, _ -> fail("Removed widget is still retained") }
        original.recycle()
        registry.close()
    }

    @Test fun `oversized updates are rejected without replacing the last good content`() {
        val registry = SceneBitmapRegistry()
        val good = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        registry.publish("widget", good)
        val excessive = Bitmap.createBitmap(SceneBitmapRegistry.MAX_BITMAP_SIZE + 1, 1, Bitmap.Config.ARGB_8888)
        try { registry.publish("widget", excessive); fail("Oversized bitmap accepted") }
        catch (_: IllegalArgumentException) { }
        registry.consumeChanged("widget", 0) { _, bitmap -> assertEquals(Color.BLUE, bitmap.getPixel(0, 0)) }
        good.recycle()
        excessive.recycle()
        registry.close()
    }
}
