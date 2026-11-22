package com.vivid.feature.streaming.compositor

import com.pedro.library.view.GlStreamInterface
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.RejectedExecutionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Regressionsschutz für #453/#445: RootEncoder schliesst den Executor in `stop()`
 * mit `shutdownNow()` und nullt das Feld danach. Ein Encoder-Teardown, der im
 * Fenster dazwischen submitted, bekommt `[Shutting down, …]` + RejectedExecution.
 */
class GlPipelineRetireTest {

    private val gl = mockk<GlStreamInterface>(relaxed = true)

    @Test
    fun `stop tolerates a teardown racing the executor shutdown`() {
        every { gl.stop() } throws RejectedExecutionException(REJECTED)

        gl.stopQuietly()

        verify(exactly = 1) { gl.stop() }
    }

    @Test
    fun `preview detach tolerates a stopped GL executor`() {
        every { gl.deAttachPreview() } throws RejectedExecutionException(REJECTED)

        gl.deAttachPreviewQuietly()

        verify(exactly = 1) { gl.deAttachPreview() }
    }

    @Test
    fun `every other failure still surfaces`() {
        every { gl.stop() } throws IllegalStateException("surface already released")
        every { gl.deAttachPreview() } throws IllegalStateException("surface already released")

        assertEquals("surface already released", assertThrows(IllegalStateException::class.java) { gl.stopQuietly() }.message)
        assertEquals("surface already released", assertThrows(IllegalStateException::class.java) { gl.deAttachPreviewQuietly() }.message)
    }

    private companion object {
        const val REJECTED =
            "Task rejected from java.util.concurrent.ThreadPoolExecutor@f6459b0[Shutting down, pool size = 1, active threads = 0]"
    }
}
