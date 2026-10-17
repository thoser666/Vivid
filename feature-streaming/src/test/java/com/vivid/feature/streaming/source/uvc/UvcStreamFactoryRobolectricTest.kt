package com.vivid.feature.streaming.source.uvc

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.pedro.common.ConnectChecker
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UvcStreamFactoryRobolectricTest {
    @Test fun customSourceInitializesOneMultiStreamWithTwoClientsWithoutOpeningUsb() {
        val source = VividUvcVideoSource(mockk(relaxed = true))
        val factory = RtmpUvcStreamFactory(ApplicationProvider.getApplicationContext<Application>(), source)
        val stream = factory.create(List(2) { mockk<ConnectChecker>(relaxed = true) })
        assertSame(source, stream.videoSource)
        assertFalse(stream.isStreaming)
        assertFalse(stream.isRecording)
        assertNotNull(stream.getStreamClient(com.pedro.library.multiple.MultiType.RTMP, 0))
        assertNotNull(stream.getStreamClient(com.pedro.library.multiple.MultiType.RTMP, 1))
        assertFalse(source.isRunning())
    }
}
