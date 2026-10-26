package com.vivid.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraModeTest {
    @Test fun `unknown settings remain standard`() {
        assertEquals(CameraMode.STANDARD, CameraMode.fromName(null))
        assertEquals(CameraMode.STANDARD, CameraMode.fromName("future"))
        assertEquals(CameraMode.EXPERIMENTAL_60_FPS, CameraMode.fromName("EXPERIMENTAL_60_FPS"))
    }
}
