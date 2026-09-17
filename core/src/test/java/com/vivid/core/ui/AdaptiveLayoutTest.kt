package com.vivid.core.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Adaptive Max-Breiten-Helfer der Window-Size-Class-Naht (UX-Audit
 * "Large Screens") - reine Funktionen, kein Android-Framework noetig.
 */
class AdaptiveLayoutTest {

    @Test
    fun `content max width caps expanded windows at 600 dp`() {
        assertEquals(
            600.dp,
            adaptiveContentMaxWidth(WindowWidthSizeClass.Expanded),
        )
    }

    @Test
    fun `content max width is unspecified on compact and medium`() {
        assertEquals(
            Dp.Unspecified,
            adaptiveContentMaxWidth(WindowWidthSizeClass.Compact),
        )
        assertEquals(
            Dp.Unspecified,
            adaptiveContentMaxWidth(WindowWidthSizeClass.Medium),
        )
    }

    @Test
    fun `controls max width grows from 220 to 320 dp on expanded`() {
        assertEquals(220.dp, adaptiveControlsMaxWidth(WindowWidthSizeClass.Compact))
        assertEquals(220.dp, adaptiveControlsMaxWidth(WindowWidthSizeClass.Medium))
        assertEquals(320.dp, adaptiveControlsMaxWidth(WindowWidthSizeClass.Expanded))
    }

}
