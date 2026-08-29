package com.vivid.feature.settings.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsCameraViewModelTest {

    private lateinit var viewModel: SettingsCameraViewModel

    @Before
    fun setUp() {
        viewModel = SettingsCameraViewModel()
    }

    // --- Focus Distance Tests ---

    @Test
    fun `initial focus distance is 0_0`() = runTest {
        assertEquals(0.0f, viewModel.focusDistance.first())
    }

    @Test
    fun `setFocusDistance updates focus distance`() = runTest {
        viewModel.setFocusDistance(5.0f)
        assertEquals(5.0f, viewModel.focusDistance.first())
    }

    @Test
    fun `setFocusDistance clamps to 0_0 minimum`() = runTest {
        viewModel.setFocusDistance(-1.0f)
        assertEquals(0.0f, viewModel.focusDistance.first())
    }

    @Test
    fun `setFocusDistance clamps to 10_0 maximum`() = runTest {
        viewModel.setFocusDistance(15.0f)
        assertEquals(10.0f, viewModel.focusDistance.first())
    }

    // --- Manual Focus Support Tests ---

    @Test
    fun `initial hasManualFocus is false`() = runTest {
        assertFalse(viewModel.hasManualFocus.first())
    }

    @Test
    fun `updateFromEngine sets hasManualFocus`() = runTest {
        viewModel.updateFromEngine(
            focusDistance = 0.0f,
            hasManualFocus = true,
            availableLenses = emptyList(),
            currentLensId = "",
        )
        assertTrue(viewModel.hasManualFocus.first())
    }

    // --- Lens Selection Tests ---

    @Test
    fun `initial available lenses is empty`() = runTest {
        assertTrue(viewModel.availableLenses.first().isEmpty())
    }

    @Test
    fun `selectLens updates current lens id`() = runTest {
        viewModel.updateFromEngine(
            focusDistance = 0.0f,
            hasManualFocus = false,
            availableLenses = listOf(
                Triple("0", "Wide", true),
                Triple("1", "Ultra-wide", false),
            ),
            currentLensId = "0",
        )

        viewModel.selectLens("1")

        assertEquals("1", viewModel.currentLensId.first())
        val lenses = viewModel.availableLenses.first()
        assertFalse(lenses[0].isActive)
        assertTrue(lenses[1].isActive)
    }

    @Test
    fun `updateFromEngine populates available lenses`() = runTest {
        viewModel.updateFromEngine(
            focusDistance = 2.0f,
            hasManualFocus = true,
            availableLenses = listOf(
                Triple("0", "Wide", true),
                Triple("1", "Ultra-wide", false),
                Triple("2", "Tele", false),
            ),
            currentLensId = "0",
        )

        val lenses = viewModel.availableLenses.first()
        assertEquals(3, lenses.size)
        assertEquals("0", lenses[0].id)
        assertEquals("Wide", lenses[0].displayName)
        assertTrue(lenses[0].isActive)
        assertEquals("1", lenses[1].id)
        assertEquals("Ultra-wide", lenses[1].displayName)
        assertFalse(lenses[1].isActive)
    }
}
