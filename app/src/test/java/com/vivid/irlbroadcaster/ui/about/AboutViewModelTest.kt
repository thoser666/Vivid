package com.vivid.irlbroadcaster.ui.about

import com.vivid.core.update.UpdateCheckResult
import com.vivid.core.update.UpdateChecker
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has no result and is not checking`() {
        val viewModel = AboutViewModel(mockk(relaxed = true))

        assertFalse(viewModel.uiState.value.checking)
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `checkForUpdates maps an available update into the state`() {
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any(), any()) } returns UpdateCheckResult.UpdateAvailable(
            latestVersion = "0.2.0-nightly.95",
            releaseUrl = "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0510",
        )
        val viewModel = AboutViewModel(checker)

        viewModel.checkForUpdates()

        val state = viewModel.uiState.value
        assertFalse(state.checking)
        assertEquals(
            UpdateCheckResult.UpdateAvailable("0.2.0-nightly.95", "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0510"),
            state.result,
        )
    }

    @Test
    fun `checkForUpdates maps an up-to-date result into the state`() {
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any(), any()) } returns UpdateCheckResult.UpToDate(latestVersion = "0.2.0-nightly.95")
        val viewModel = AboutViewModel(checker)

        viewModel.checkForUpdates()

        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.95"), viewModel.uiState.value.result)
    }

    @Test
    fun `checkForUpdates maps an error into the state`() {
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any(), any()) } returns UpdateCheckResult.Error("Update-Check fehlgeschlagen: network down")
        val viewModel = AboutViewModel(checker)

        viewModel.checkForUpdates()

        assertEquals(UpdateCheckResult.Error("Update-Check fehlgeschlagen: network down"), viewModel.uiState.value.result)
    }

    @Test
    fun `checkForUpdates is ignored while a check is running`() {
        val gate = CompletableDeferred<UpdateCheckResult>()
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any(), any()) } coAnswers { gate.await() }
        val viewModel = AboutViewModel(checker)

        viewModel.checkForUpdates() // startet und blockiert am gate
        assertTrue(viewModel.uiState.value.checking)

        viewModel.checkForUpdates() // muss ignoriert werden (checking == true)

        gate.complete(UpdateCheckResult.UpToDate("0.2.0-nightly.95"))
        assertFalse(viewModel.uiState.value.checking)
        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.95"), viewModel.uiState.value.result)
    }
}
