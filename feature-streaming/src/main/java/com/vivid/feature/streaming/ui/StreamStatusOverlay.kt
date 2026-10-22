package com.vivid.feature.streaming.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivid.feature.streaming.R
import com.vivid.feature.streaming.StreamTargetState
import com.vivid.feature.streaming.StreamTargetStatus
import kotlinx.coroutines.delay

/** Targets share an encoder; use one live measurement instead of summing duplicate streams. */
internal fun currentStreamBitrateKbps(targets: List<StreamTargetState>): Int? = targets
    .firstOrNull { it.status == StreamTargetStatus.STREAMING && it.bitrateKbps != null }
    ?.bitrateKbps

internal fun elapsedStreamSeconds(startedAtMs: Long?, nowMs: Long): Long =
    if (startedAtMs == null) 0 else ((nowMs - startedAtMs).coerceAtLeast(0) / 1_000)

/** Local preview HUD; its timer follows the engine session, not the composition lifetime. */
@Composable
internal fun StreamStatusOverlay(
    modeFps: Int,
    bitrateKbps: Int?,
    startedAtMs: Long?,
    modifier: Modifier = Modifier,
    nowMs: () -> Long = SystemClock::elapsedRealtime,
) {
    val elapsedSeconds by produceState(elapsedStreamSeconds(startedAtMs, nowMs()), startedAtMs) {
        while (true) {
            value = elapsedStreamSeconds(startedAtMs, nowMs())
            delay(1_000)
        }
    }
    val bitrate = when {
        bitrateKbps == null -> stringResource(R.string.streaming_bitrate_pending)
        bitrateKbps >= 1_000 -> stringResource(R.string.streaming_target_bitrate_mbps, bitrateKbps / 1_000.0)
        else -> stringResource(R.string.streaming_target_bitrate_kbps, bitrateKbps.toString())
    }
    Row(modifier.testTag("stream_status_overlay"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusText(stringResource(R.string.streaming_mode_fps, modeFps), "stream_mode_fps")
        StatusText(bitrate, "stream_current_bitrate")
        StatusText(stringResource(R.string.streaming_uptime,
            elapsedSeconds / 3_600, (elapsedSeconds / 60) % 60, elapsedSeconds % 60), "stream_uptime")
    }
}

@Composable
private fun StatusText(text: String, tag: String) {
    Text(
        text,
        modifier = Modifier.testTag(tag)
            .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        color = Color.White.copy(alpha = 0.85f),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
    )
}
