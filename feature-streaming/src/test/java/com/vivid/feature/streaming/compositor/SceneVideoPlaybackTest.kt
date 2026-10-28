package com.vivid.feature.streaming.compositor

import com.vivid.feature.streaming.compositor.capture.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneVideoPlaybackTest {
    private class Worker : SceneVideoWorker {
        var closed = false
        var calls = 0
        override fun <T> call(action: () -> T): T { check(!closed); calls++; return action() }
        override fun close() { closed = true }
    }
    private class Player : SceneVideoPlayer {
        lateinit var prepared: () -> Unit
        lateinit var completed: () -> Unit
        lateinit var failed: () -> Unit
        var starts = 0
        var releases = 0
        var rewinds = 0
        var position = 12.5
        var autoSeekComplete = true
        var rewindComplete: (() -> Unit)? = null
        var prepareFailure: Exception? = null
        override fun prepare(prepared: () -> Unit, completed: () -> Unit, failed: () -> Unit) {
            this.prepared = prepared; this.completed = completed; this.failed = failed
            prepareFailure?.let { throw it }
        }
        override fun start() { starts++ }
        override fun rewind(completed: () -> Unit) {
            rewinds++; rewindComplete = completed
            if (autoSeekComplete) completeRewind()
        }
        fun completeRewind() { position = 0.0; rewindComplete?.invoke(); rewindComplete = null }
        override fun positionSeconds() = position
        override fun release() { releases++ }
    }
    private val player = Player()
    private val worker = Worker()
    private val loops = mutableListOf<SceneVideoPlayback>()
    private var failures = 0
    private val producer = SceneVideoProducer({ player }, { failures++ }, loops::add, worker)

    @Test fun `audio playhead waits for preparation and a usable video frame`() {
        producer.start()
        assertNull(producer.playback())
        producer.frameReady()
        assertNull(producer.playback())
        player.prepared()
        assertEquals(1, player.starts)
        assertNull(producer.playback())
        producer.frameReady()
        assertEquals(12.5, producer.playback()!!.positionSeconds)
        val calls = worker.calls
        repeat(60) { producer.frameReady() }
        assertEquals(calls, worker.calls, "Usable frames must not synchronously dispatch every GL frame")
        producer.release()
    }
    @Test fun `video owns looping and announces one cycle with the same capture identity`() {
        producer.start(); player.prepared(); producer.frameReady()
        val first = producer.playback()!!
        repeat(3) { player.completed() }
        assertEquals(3, player.rewinds)
        assertEquals(listOf(1L, 2L, 3L), loops.map { it.cycle })
        assertTrue(loops.all { it.identity === first.identity && it.positionSeconds == 0.0 })
        assertEquals(3L, producer.playback()!!.cycle)
        producer.release()
    }
    @Test fun `audio waits for asynchronous seek completion instead of observing the previous EOF position`() {
        producer.start(); player.prepared(); producer.frameReady()
        player.autoSeekComplete = false
        player.completed()
        player.completed() // A duplicate EOS must not start a second seek.
        assertEquals(1, player.rewinds)
        assertTrue(loops.isEmpty())
        assertNull(producer.playback())
        player.completeRewind()
        assertEquals(1, loops.size)
        assertEquals(0.0, producer.playback()!!.positionSeconds)
        producer.release()
    }
    @Test fun `release drains the player before closing its worker and ignores queued callbacks`() {
        producer.start(); player.prepared(); producer.frameReady()
        producer.stop(); producer.release()
        assertTrue(worker.closed)
        assertEquals(1, player.releases)
        player.prepared(); player.completed(); player.failed()
        producer.frameReady()
        assertNull(producer.playback())
        assertTrue(loops.isEmpty()); assertEquals(0, failures); assertEquals(1, player.starts)
    }
    @Test fun `errors make the playhead unavailable and report once`() {
        producer.start(); player.prepared(); producer.frameReady()
        player.failed(); player.failed()
        assertNull(producer.playback())
        assertEquals(1, failures)
        producer.release()
    }
    @Test fun `partially prepared players are released on failure`() {
        player.prepareFailure = IllegalArgumentException("bad file")
        assertThrows(IllegalArgumentException::class.java) { producer.start() }
        assertEquals(1, player.releases)
        producer.release()
        assertEquals(1, player.releases)
    }
    @Test fun `a non looping movie retains its last frame without restarting or announcing a replay loop`() {
        val movie = SceneVideoProducer({ player }, { failures++ }, loops::add, worker, loop = false)
        movie.start(); player.prepared()
        assertFalse(movie.retainsLastFrame())
        movie.frameReady()
        player.position = 20.0
        player.completed(); player.completed()
        assertEquals(0, player.rewinds)
        assertTrue(loops.isEmpty())
        assertTrue(movie.retainsLastFrame())
        assertEquals(20.0, movie.playback()!!.positionSeconds)
        player.failed()
        assertFalse(movie.retainsLastFrame())
        movie.release()
        assertFalse(movie.retainsLastFrame())
    }

}
