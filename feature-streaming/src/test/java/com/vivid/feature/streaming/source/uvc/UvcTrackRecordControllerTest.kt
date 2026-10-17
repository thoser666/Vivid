package com.vivid.feature.streaming.source.uvc

import android.media.MediaFormat
import com.pedro.library.base.recording.RecordController
import io.mockk.*
import org.junit.jupiter.api.Test

class UvcTrackRecordControllerTest {
    @Test fun `track policy changes without replacing controller or dropping earlier codec formats`() {
        val muxer = mockk<RecordController>(relaxed = true)
        val controller = UvcTrackRecordController(muxer)
        val video = mockk<MediaFormat>()
        val audio = mockk<MediaFormat>()
        controller.setVideoFormat(video)
        controller.setAudioFormat(audio)
        controller.includeAudio = false
        controller.startRecord("video.mp4", null, RecordController.RecordTracks.ALL)
        controller.stopRecord()
        controller.includeAudio = true
        controller.startRecord("audio-video.mp4", null, RecordController.RecordTracks.ALL)
        verifyOrder {
            muxer.setVideoFormat(video)
            muxer.setAudioFormat(audio)
            muxer.startRecord("video.mp4", null, RecordController.RecordTracks.VIDEO)
            muxer.stopRecord()
            muxer.startRecord("audio-video.mp4", null, RecordController.RecordTracks.ALL)
        }
        verify(exactly = 0) { muxer.resetFormats() }
    }
}
