package com.vivid.feature.widget

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QrCodeWidgetTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `generator creates a non-empty square matrix`() {
        val matrix = QrCodeGenerator.generateMatrix("https://example.com/donate")

        assertTrue(matrix.width > 0)
        assertEquals(matrix.width, matrix.height)
        assertTrue((0 until matrix.width).any { x -> (0 until matrix.height).any { y -> matrix[x, y] } })
    }

    @Test
    fun `generator clamps requested bitmap size`() {
        assertEquals(120, QrCodeGenerator.outputSize(20))
        assertEquals(240, QrCodeGenerator.outputSize(240))
        assertEquals(600, QrCodeGenerator.outputSize(720))
    }

    @Test
    fun `generator returns null for blank content`() {
        assertNull(QrCodeGenerator.generateBitmap("   "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `matrix generation rejects blank content`() {
        QrCodeGenerator.generateMatrix("\n")
    }

    @Test
    fun `view model mirrors persisted qr settings`() = runTest {
        val flow = MutableStateFlow(
            AppSettings(
                qrCodeWidgetEnabled = true,
                qrCodeWidgetContent = "https://example.com/donate",
                qrCodeWidgetSizeDp = 240,
                qrCodeWidgetOpacity = 0.7f,
            ),
        )
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns flow
        }
        val viewModel = QrCodeWidgetViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.enabled)
        assertEquals("https://example.com/donate", viewModel.uiState.value.content)
        assertEquals(240, viewModel.uiState.value.sizeDp)
        assertEquals(0.7f, viewModel.uiState.value.opacity, 0.001f)

        flow.value = AppSettings()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.enabled)
        assertEquals("", viewModel.uiState.value.content)
    }
}
