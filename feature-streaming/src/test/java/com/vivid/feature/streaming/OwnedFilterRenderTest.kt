package com.vivid.feature.streaming

import android.content.Context
import com.pedro.encoder.input.gl.render.RenderHandler
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OwnedFilterRenderTest {
    @Test fun `owned framebuffer is released once per initialized filter lifetime`() {
        val handler = RenderHandler()
        val delegate = mockk<BaseFilterRender>(relaxed = true) { every { renderHandler } returns handler }
        val released = mutableListOf<RenderHandler>()
        val filter = OwnedFilterRender(delegate) { released.add(it) }
        val context = mockk<Context>(relaxed = true)
        repeat(3) {
            filter.initGl(1920, 1080, context, 1920, 1080)
            filter.initFBOLink()
            filter.setPreviousTexId(42)
            filter.draw()
            filter.release()
            filter.release()
        }
        assertEquals(listOf(handler, handler, handler), released)
        verify(exactly = 3) { delegate.release(); delegate.draw(); delegate.setPreviousTexId(42) }
    }
}
