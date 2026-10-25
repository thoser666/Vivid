package com.vivid.feature.streaming.compositor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.view.Surface
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.compositor.capture.*
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.Closeable
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SceneUsbProducerRobolectricTest {
    private class Worker : AbstractExecutorService() {
        val tasks = ArrayDeque<Runnable>()
        var retired = false
        override fun execute(command: Runnable) { if (retired) throw RejectedExecutionException(); tasks.addLast(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
        override fun shutdown() { retired = true }
        override fun shutdownNow(): MutableList<Runnable> { retired = true; return tasks.toMutableList().also { tasks.clear() } }
        override fun isShutdown() = retired
        override fun isTerminated() = retired && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }
    private val workers = mutableListOf<Worker>()
    private val device = mockk<UsbDevice> { every { deviceName } returns "/dev/bus/usb/001/002" }
    private var selection: SceneUsbSelection = SceneUsbSelection.Selected(device, true)
    private val devices = mockk<SceneUsbDevices> { every { resolve(any()) } answers { selection } }
    private val context = mockk<Context> {
        every { checkSelfPermission(Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
    }
    private val driver = mockk<SceneUsbDriver>(relaxed = true)
    private val mode = SceneUsbMode(1280, 720, SceneUsbFormat.MJPEG, setOf(30, 60))
    private var current: SceneUsbConfiguration? = null
    private val source = SceneSource("usb", SceneSourceType.UVC, SceneUsbLocator(1, 2, "serial", device.deviceName).encode())
    private val target = SceneCaptureTarget(mockk(relaxed = true), mockk<Surface>(relaxed = true), 1280, 720)
    private var detached: ((String) -> Unit)? = null
    private var watchCloses = 0
    private var factories = 0
    private val failures = mutableListOf<Boolean>()
    private val backend = AndroidSceneUsbCaptureBackend(context, devices, {
        factories++; driver
    }, { Worker().also { workers.add(it) } }, { callback ->
        detached = callback; Closeable { watchCloses++ }
    })
    init {
        every { driver.open() } returns listOf(mode)
        every { driver.start(any(), any()) } answers { current = secondArg() }
    }
    private fun create(id: String = source.id) = backend.create(source.copy(id = id), target) { failures.add(it) }
    private fun drain() { workers.toList().forEach { it.drain() } }
    @After fun close() { backend.release(); drain() }

    @Test fun `capture opens on the worker and borrows one Surface without creating an encoder`() {
        backend.configure(listOf(source))
        val producer = create(); producer.start()
        assertEquals(0, factories)
        drain()
        assertEquals(1, factories)
        verify(exactly = 1) { driver.open(); driver.start(target.surface, SceneUsbConfiguration(mode, 30)) }
        verify(exactly = 0) { target.surface.release(); target.texture.release() }
        producer.stop(); producer.release(); drain()
        verify(exactly = 1) { driver.close() }
    }
    @Test fun `stop before queued open does not allocate a driver or touch hardware`() {
        val producer = create(); producer.start(); producer.stop(); drain()
        assertEquals(0, factories)
        assertTrue(failures.isEmpty())
    }
    @Test fun `missing permission reports a permission state without loading the native driver`() {
        selection = SceneUsbSelection.Selected(device, false)
        create().start(); drain()
        assertEquals(listOf(true), failures)
        assertEquals(0, factories)
    }
    @Test fun `unavailable and ambiguous devices remain offline`() {
        selection = SceneUsbSelection.Ambiguous
        create().start(); drain()
        assertEquals(listOf(false), failures)
        assertEquals(0, factories)
    }
    @Test fun `a duplicate physical camera never opens a second driver`() {
        create().start(); create("alias").start(); drain()
        assertEquals(1, factories)
        assertEquals(listOf(false), failures)
        verify(exactly = 0) { driver.close() }
    }
    @Test fun `pending rate changes coalesce on the existing open driver and Surface`() {
        create().start(); drain()
        repeat(5) { backend.setTargetFrameRate(60) }
        assertEquals(1, workers.single().tasks.size)
        drain()
        assertEquals(60, current?.frameRate)
        verify(exactly = 1) { driver.open() }
        verify(exactly = 2) { driver.start(target.surface, any()) }
    }
    @Test fun `a failed preferred rate restarts the previous mode rather than assuming it still runs`() {
        create().start(); drain()
        every { driver.start(any(), any()) } answers {
            val config = secondArg<SceneUsbConfiguration>()
            current = null // Native reconfiguration stops the previous preview before negotiation.
            if (config.frameRate == 60) throw IllegalArgumentException("mode negotiation failed")
            current = config
        }
        backend.setTargetFrameRate(60); drain()
        assertEquals(30, current?.frameRate)
        assertTrue(failures.isEmpty())
        verify(exactly = 1) { driver.open() }
    }
    @Test fun `repeated failed rate negotiation restores preview and stops retrying after three attempts`() {
        create().start(); drain()
        var calls = 0
        every { driver.start(any(), any()) } answers {
            calls++
            if (calls % 3 != 0) throw IllegalArgumentException("mode rejected")
            current = secondArg()
        }
        repeat(10) { backend.setTargetFrameRate(60); drain() }
        assertEquals(9, calls)
        assertEquals(30, current?.frameRate)
        assertTrue(failures.isEmpty())
        verify(exactly = 1) { driver.open() }
        backend.setTargetFrameRate(30); drain()
        backend.setTargetFrameRate(60); drain()
        assertEquals(12, calls)
    }
    @Test fun `stop cancels a queued rate change before native reconfiguration`() {
        val producer = create(); producer.start(); drain()
        backend.setTargetFrameRate(60)
        producer.stop(); drain()
        verify(exactly = 1) { driver.start(target.surface, any()); driver.close() }
        assertTrue(failures.isEmpty())
    }
    @Test fun `device detach closes the source and a late event cannot affect a replacement`() {
        create().start(); drain()
        requireNotNull(detached).invoke(device.deviceName); drain()
        assertEquals(listOf(false), failures)
        verify(exactly = 1) { driver.close() }
        requireNotNull(detached).invoke(device.deviceName)
        assertEquals(1, failures.size)
    }
    @Test fun `native load failure becomes an offline source and retires its resources`() {
        every { driver.open() } throws UnsatisfiedLinkError("native ABI unavailable")
        create().start(); drain()
        assertEquals(listOf(false), failures)
        verify(exactly = 1) { driver.close() }
    }
    @Test fun `backend release retires every worker and its private watcher once`() {
        create().start(); drain()
        backend.release(); backend.release(); drain()
        assertEquals(1, watchCloses)
        assertTrue(workers.all { it.isShutdown })
        assertTrue(failures.isEmpty())
    }
}
