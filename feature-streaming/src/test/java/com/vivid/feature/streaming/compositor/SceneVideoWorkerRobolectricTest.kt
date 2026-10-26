package com.vivid.feature.streaming.compositor

import com.vivid.feature.streaming.compositor.capture.AndroidSceneVideoWorker
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
class SceneVideoWorkerRobolectricTest {
    @Test fun `player operations share a real background Looper and nested calls do not deadlock`() {
        val owner = Thread.currentThread()
        val worker = AndroidSceneVideoWorker()
        try {
            val capture = worker.call { Thread.currentThread() }
            assertNotSame(owner, capture)
            assertSame(capture, worker.call { worker.call { Thread.currentThread() } })
            assertEquals("VividSceneVideo", capture.name)
            val error = IllegalArgumentException("prepare failed")
            assertSame(error, assertThrows(IllegalArgumentException::class.java) { worker.call { throw error } })
            assertEquals(42, worker.call { 42 })
        } finally { worker.close() }
    }
    @Test fun `an interrupted caller still drains release work and retains its interrupt flag`() {
        val worker = AndroidSceneVideoWorker()
        try {
            Thread.currentThread().interrupt()
            assertEquals("released", worker.call { "released" })
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
            worker.close()
        }
    }
}
