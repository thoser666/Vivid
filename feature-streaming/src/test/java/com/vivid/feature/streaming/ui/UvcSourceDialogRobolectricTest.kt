package com.vivid.feature.streaming.ui

import android.Manifest
import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.vivid.feature.streaming.*
import com.vivid.feature.streaming.source.uvc.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UvcSourceDialogRobolectricTest {
    @get:Rule val compose = createComposeRule()
    private val engine = mockk<StreamingEngine>(relaxed = true)
    private val manager = mockk<UvcConnectionManager>(relaxed = true)
    private val devices = MutableStateFlow<List<UvcDeviceDescriptor>>(emptyList())
    private val connection = MutableStateFlow<UvcConnectionState>(UvcConnectionState.Disconnected)

    @Before fun setup() {
        every { engine.uvcManager } returns manager
        every { manager.devices } returns devices
        every { manager.connectionState } returns connection
        every { manager.notice } returns MutableStateFlow(null)
        every { manager.hasUsbHostFeature() } returns true
        every { engine.streamingState } returns MutableStateFlow(StreamingState.Idle)
        every { engine.replayState } returns MutableStateFlow(ReplayState.Idle)
    }
    private fun show() { compose.setContent { MaterialTheme { UvcSourceDialog(engine) {} } } }

    @Test fun emptyPickerNeverRequestsPermissionOrOpensCapture() {
        show()
        compose.onNodeWithTag("uvc_picker").assertExists()
        compose.onNodeWithTag("uvc_connect").assertIsNotEnabled()
        verify(exactly = 0) { engine.connectUsbDevice(any(), any()) }
        verify(exactly = 0) { manager.connect(any(), any()) }
    }
    @Test fun selectingDeviceAloneDoesNotConnectAndExplicitConnectUsesExactInstance() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.CAMERA)
        val first = UvcDeviceDescriptor("first", 1, 2, "Webcam")
        val second = first.copy(stableSessionId = "second")
        devices.value = listOf(first, second)
        show()
        compose.onNodeWithTag("uvc_device_1").performClick()
        verify(exactly = 0) { engine.connectUsbDevice(any(), any()) }
        compose.onNodeWithTag("uvc_connect").performClick()
        verify(exactly = 1) { engine.connectUsbDevice(second, null) }
    }
    @Test fun deniedConnectionDisplaysAnErrorAndAllowsExplicitRetry() {
        devices.value = listOf(UvcDeviceDescriptor("first", 1, 2, "Webcam"))
        connection.value = UvcConnectionState.Error(UvcFailure.PermissionDenied)
        show()
        compose.onNodeWithTag("uvc_status").assertExists()
        compose.onNodeWithTag("uvc_device_0").performClick()
        compose.onNodeWithTag("uvc_connect").assertIsEnabled()
    }
}
