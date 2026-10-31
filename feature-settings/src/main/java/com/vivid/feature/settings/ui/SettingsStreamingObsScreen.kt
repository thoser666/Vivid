package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vivid.core.data.AppSettings
import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.ReplayAudioMode
import com.vivid.core.data.VideoCodecPreference
import com.vivid.feature.chat.twitch.TwitchChannelUiState
import com.vivid.feature.chat.twitch.TwitchChannelViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.vivid.feature.streaming.ConfigIssueSeverity
import com.vivid.feature.streaming.StreamConfigIssue
import com.vivid.feature.streaming.StreamConfigValidator
import androidx.compose.ui.semantics.contentDescription
import com.vivid.core.data.StreamProfile
import com.vivid.core.data.StreamPlatformType
import com.vivid.core.data.applyTo
import kotlin.math.roundToInt

private const val STREAM_PAGE_LIST = "list"
private const val STREAM_PAGE_PLATFORM = "platform"
private const val STREAM_PAGE_CREATE = "create"
private const val STREAM_PAGE_DETAILS = "details"
private const val STREAM_PAGE_VIDEO = "video"
private const val STREAM_PAGE_MULTI = "multi"
private const val STREAM_PAGE_RECORDING = "recording"
private const val STREAM_PAGE_TWITCH = "twitch"
private const val STREAM_PAGE_OBS = "obs"

/** Saved streams, with separate navigation and toggle actions on each row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsStreamingObsScreen(
    uiState: AppSettings,
    viewModel: SettingsViewModel,
    twitchViewModel: TwitchChannelViewModel,
    twitchState: TwitchChannelUiState,
    onBack: () -> Unit,
) {
    val draft by viewModel.streamDraft.collectAsState()
    val saving by viewModel.streamSaving.collectAsState()
    val failed by viewModel.streamSaveFailed.collectAsState()
    var page by rememberSaveable { mutableStateOf(STREAM_PAGE_LIST) }
    val scrollState = rememberScrollState()
    LaunchedEffect(page) { scrollState.scrollTo(0) }
    LaunchedEffect(failed) { if (failed) scrollState.scrollTo(0) }
    val back: () -> Unit = { streamPageBack(page, saving, viewModel, onBack) { page = it } }
    BackHandler(enabled = page != STREAM_PAGE_LIST, onBack = back)
    LaunchedEffect(page, draft) {
        if (page != STREAM_PAGE_LIST && page != STREAM_PAGE_PLATFORM && draft == null) page = STREAM_PAGE_LIST
    }
    val title = streamPageTitle(page, draft)
    SettingsSectionScaffold(
        title = title,
        scrollState = scrollState,
        onBack = back,
        onSave = if (editableStreamPage(page, draft)) {
            { viewModel.saveStreamDraft { page = STREAM_PAGE_LIST } }
        } else null,
        saveEnabled = streamDraftSaveEnabled(draft, saving),
    ) {
        StreamDraftFeedback(page, draft, failed)
        when (page) {
            STREAM_PAGE_LIST -> {
                StreamProfilesList(
                    profiles = uiState.streamProfiles,
                    activeId = uiState.activeStreamId,
                    scrollState = scrollState,
                    onOpen = { viewModel.editStream(it); page = STREAM_PAGE_DETAILS },
                    onToggle = viewModel::setStreamEnabled,
                    onReorder = viewModel::reorderStreams,
                )
                Button(onClick = { page = STREAM_PAGE_PLATFORM }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.stream_create))
                }
            }
            STREAM_PAGE_PLATFORM -> StreamPlatform.entries.forEach { platform ->
                Button(onClick = { viewModel.createStream(platform); page = STREAM_PAGE_CREATE }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(platform.labelRes))
                }
            }
            else -> draft?.let { profile ->
                StreamProfilePage(page, profile, uiState, viewModel, twitchViewModel, twitchState) { page = it }
            }
        }
    }
}

private fun streamPageBack(
    page: String, saving: Boolean, viewModel: SettingsViewModel,
    onBack: () -> Unit, navigate: (String) -> Unit,
) {
    if (!saving) when (page) {
        STREAM_PAGE_LIST -> onBack()
        STREAM_PAGE_PLATFORM, STREAM_PAGE_CREATE, STREAM_PAGE_DETAILS -> {
            viewModel.discardStreamDraft()
            navigate(STREAM_PAGE_LIST)
        }
        else -> navigate(STREAM_PAGE_DETAILS)
    }
}

@Composable
private fun streamPageTitle(page: String, draft: StreamProfile?): String = when (page) {
    STREAM_PAGE_PLATFORM -> stringResource(R.string.stream_choose_type)
    STREAM_PAGE_CREATE -> stringResource(R.string.stream_create)
    STREAM_PAGE_DETAILS -> draft?.name.orEmpty()
    STREAM_PAGE_VIDEO -> stringResource(R.string.stream_video)
    STREAM_PAGE_MULTI -> stringResource(R.string.multi_streaming_title)
    STREAM_PAGE_RECORDING -> stringResource(R.string.replay_audio_title)
    STREAM_PAGE_TWITCH -> stringResource(R.string.twitch_channel_title)
    STREAM_PAGE_OBS -> stringResource(R.string.stream_obs_remote)
    else -> stringResource(R.string.cat_streaming_title)
}

private fun editableStreamPage(page: String, draft: StreamProfile?): Boolean =
    draft != null && page != STREAM_PAGE_LIST && page != STREAM_PAGE_PLATFORM

private fun streamDraftSaveEnabled(draft: StreamProfile?, saving: Boolean): Boolean {
    if (draft == null) return false
    return !saving && draft.name.isNotBlank() && streamDraftErrors(draft).isEmpty()
}

@Composable
private fun StreamDraftFeedback(page: String, draft: StreamProfile?, failed: Boolean) {
    if (failed) Text(stringResource(R.string.stream_save_failed), color = MaterialTheme.colorScheme.error)
    // Errors in another subpage must still explain why its Save button is disabled.
    draft?.let(::streamDraftErrors).orEmpty().filter { issue ->
        when (page) {
            STREAM_PAGE_CREATE, STREAM_PAGE_DETAILS -> issue.prefixRes != 0
            STREAM_PAGE_MULTI -> issue.prefixRes == 0
            else -> true
        }
    }.forEach { issue ->
        val prefix = if (issue.prefixRes != 0) stringResource(issue.prefixRes) else ""
        Text(prefix + stringResource(issue.messageRes, *issue.formatArgs.toTypedArray()),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** A menu group with inset separators, matching the two independent row actions. */
@Composable
private fun StreamMenuGroup(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().testTag("stream_settings_menu")) { content() }
    }
}

@Composable
internal fun StreamSwitchAction(tag: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VerticalDivider(modifier = Modifier.height(32.dp).testTag(tag))
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun StreamSettingLink(
    label: String,
    onClick: () -> Unit,
    divider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = trailing,
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
    if (divider) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamProfilePage(
    page: String,
    profile: StreamProfile,
    settings: AppSettings,
    viewModel: SettingsViewModel,
    twitchViewModel: TwitchChannelViewModel,
    twitchState: TwitchChannelUiState,
    navigate: (String) -> Unit,
) {
    val uiState = profile.applyTo(settings)
    val obsLabel = stringResource(R.string.stream_obs_remote)
    when (page) {
        STREAM_PAGE_CREATE, STREAM_PAGE_DETAILS -> {
            OutlinedTextField(value = profile.name,
                onValueChange = { value -> viewModel.updateStreamDraft { it.copy(name = value) } },
                label = { Text(stringResource(R.string.stream_name)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            StreamUrlField(profile.streamUrl, R.string.stream_url_label,
                streamDraftErrors(profile).filter { it.prefixRes == 0 }, "stream_primary_url") { value ->
                viewModel.updateStreamDraft { it.copy(streamUrl = value) }
            }
            Text(stringResource(STREAM_URL_HINT_RES), style = MaterialTheme.typography.bodySmall)
            SecretField(value = profile.streamKey,
                onValueChange = { value -> viewModel.updateStreamDraft { it.copy(streamKey = value) } },
                labelRes = R.string.stream_key_label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stream_tls_label), modifier = Modifier.weight(1f))
                Checkbox(checked = profile.streamUseTls,
                    onCheckedChange = { value -> viewModel.updateStreamDraft { it.copy(streamUseTls = value) } })
            }
            if (page == STREAM_PAGE_DETAILS) StreamMenuGroup {
                StreamSettingLink(stringResource(R.string.stream_video), { navigate(STREAM_PAGE_VIDEO) })
                StreamSettingLink(stringResource(R.string.replay_audio_title), { navigate(STREAM_PAGE_RECORDING) })
                if (profile.platform == StreamPlatformType.Twitch) {
                    StreamSettingLink(stringResource(R.string.twitch_channel_title), { navigate(STREAM_PAGE_TWITCH) })
                }
                StreamSettingLink(stringResource(R.string.multi_streaming_title), { navigate(STREAM_PAGE_MULTI) })
                StreamSettingLink(stringResource(R.string.stream_obs_remote), { navigate(STREAM_PAGE_OBS) }, divider = false, trailing = {
                    StreamSwitchAction("obs_switch_divider") {
                        Switch(checked = profile.obsRemoteEnabled,
                            onCheckedChange = { value -> viewModel.updateStreamDraft { it.copy(obsRemoteEnabled = value) } },
                            modifier = Modifier.testTag("obs_profile_toggle").semantics { contentDescription = obsLabel })
                    }
                })
            }
        }
        STREAM_PAGE_VIDEO -> StreamVideoSettings(profile, uiState, viewModel)
        STREAM_PAGE_RECORDING -> StreamRecordingSettings(uiState, viewModel)
        STREAM_PAGE_TWITCH -> StreamTwitchSettings(uiState, viewModel, twitchViewModel, twitchState)
        STREAM_PAGE_MULTI -> StreamMultiSettings(uiState, viewModel)
        STREAM_PAGE_OBS -> StreamObsSettings(uiState, viewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamVideoSettings(profile: StreamProfile, uiState: AppSettings, viewModel: SettingsViewModel) {
    val bitrateLabel = stringResource(R.string.stream_bitrate)
    val keyframeLabel = stringResource(R.string.stream_keyframe)
    Text(stringResource(R.string.encoder_preset_title), style = MaterialTheme.typography.titleMedium)
    StreamEncoderSettings(uiState, viewModel)
    HorizontalDivider()
    Text(stringResource(R.string.stream_fps), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60).forEach { fps ->
                FilterChip(selected = profile.videoFps == fps,
                    onClick = { viewModel.updateStreamDraft { it.copy(videoFps = fps) } },
                    label = { Text(stringResource(R.string.stream_fps_value, fps)) })
            }
        }
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.stream_llb), modifier = Modifier.weight(1f))
            Switch(checked = profile.lowLightBoostEnabled,
                onCheckedChange = { value -> viewModel.updateStreamDraft { it.copy(lowLightBoostEnabled = value) } })
        }
    HorizontalDivider()
    Text(stringResource(R.string.stream_bitrate), style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stream_bitrate_value, profile.videoBitrateKbps / 1_000))
            Slider(value = (profile.videoBitrateKbps / 1_000f).coerceIn(1f, 15f), valueRange = 1f..15f, steps = 13,
                onValueChange = { value -> viewModel.updateStreamDraft { it.copy(videoBitrateKbps = value.roundToInt() * 1_000) } },
                modifier = Modifier.semantics { contentDescription = bitrateLabel })
        }
    HorizontalDivider()
    Text(stringResource(R.string.stream_keyframe), style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stream_keyframe_value, profile.keyframeIntervalSeconds))
            Slider(value = profile.keyframeIntervalSeconds.toFloat(), valueRange = 1f..10f, steps = 8,
                onValueChange = { value -> viewModel.updateStreamDraft { it.copy(keyframeIntervalSeconds = value.roundToInt()) } },
                modifier = Modifier.semantics { contentDescription = keyframeLabel })
        }
}

@Composable
private fun StreamRecordingSettings(uiState: AppSettings, viewModel: SettingsViewModel) {
    // Replay-Aufnahme (Record-to-Disk): Audio-Konfiguration der MP4.
    Text(
        text = stringResource(R.string.replay_audio_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.replay_audio_enabled), modifier = Modifier.weight(1f))
        Switch(
            checked = uiState.replayAudioMode == ReplayAudioMode.ALL,
            onCheckedChange = { checked ->
                viewModel.updateStreamDraft { it.copy(replayAudioMode = if (checked) ReplayAudioMode.ALL else ReplayAudioMode.VIDEO_ONLY) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamEncoderSettings(uiState: AppSettings, viewModel: SettingsViewModel) {
    // Encoder-Presets: Aufloesung/FPS + Codec (4K/60fps + HEVC).
    Text(
        text = stringResource(R.string.encoder_preset_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EncoderPreset.entries.forEach { preset ->
            // A11y: Auswahlzustand für TalkBack (FilterChip selected allein
            // liefert keinen lokalisierten Zustandstext).
            val chipState = stringResource(
                if (preset == uiState.encoderPreset) R.string.a11y_selected else R.string.a11y_not_selected,
            )
            FilterChip(
                selected = uiState.encoderPreset == preset,
                onClick = { viewModel.updateStreamDraft { it.copy(encoderPreset = preset, videoFps = preset.fps) } },
                label = { Text(preset.displayName()) },
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    stateDescription = chipState
                },
            )
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VideoCodecPreference.entries.forEach { preference ->
            val codecState = stringResource(
                if (preference == uiState.videoCodecPreference) R.string.a11y_selected else R.string.a11y_not_selected,
            )
            FilterChip(
                selected = uiState.videoCodecPreference == preference,
                onClick = { viewModel.updateStreamDraft { it.copy(videoCodecPreference = preference) } },
                label = { Text(stringResource(preference.labelRes())) },
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    stateDescription = codecState
                },
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.encoder_auto_fallback), modifier = Modifier.weight(1f))
        val switchOn = stringResource(R.string.a11y_switch_on)
        val switchOff = stringResource(R.string.a11y_switch_off)
        Switch(
            checked = uiState.encoderAutoFallback,
            onCheckedChange = { value -> viewModel.updateStreamDraft { it.copy(encoderAutoFallback = value) } },
            modifier = Modifier.semantics {
                stateDescription = if (uiState.encoderAutoFallback) switchOn else switchOff
            },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.adaptive_bitrate), modifier = Modifier.weight(1f))
        Switch(
            checked = uiState.adaptiveBitrateEnabled,
            onCheckedChange = { value -> viewModel.updateStreamDraft { it.copy(adaptiveBitrateEnabled = value) } },
        )
    }
}

@Composable
private fun StreamTwitchSettings(uiState: AppSettings, viewModel: SettingsViewModel, twitchViewModel: TwitchChannelViewModel, twitchState: TwitchChannelUiState) {
    // Twitch-Kanalintegration: Live-Viewer sowie Titel/Kategorie über Helix.
    Text(
        text = stringResource(R.string.twitch_channel_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SecretField(
        value = uiState.twitchChannelOauthToken,
        onValueChange = { value -> viewModel.updateStreamDraft { it.copy(twitchChannelOauthToken = value) } },
        labelRes = R.string.twitch_channel_oauth_label,
    )
    OutlinedTextField(
        value = uiState.twitchStreamTitle,
        onValueChange = { value -> viewModel.updateStreamDraft { it.copy(twitchStreamTitle = value) } },
        label = { Text(stringResource(R.string.twitch_stream_title_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = uiState.twitchStreamCategory,
        onValueChange = { value -> viewModel.updateStreamDraft { it.copy(twitchStreamCategory = value) } },
        label = { Text(stringResource(R.string.twitch_stream_category_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { twitchViewModel.refreshForStream(uiState) },
            enabled = !twitchState.loading,
        ) {
            Text(stringResource(R.string.twitch_refresh_viewers))
        }
        twitchState.streamInfo?.let { info ->
            Text(pluralStringResource(R.plurals.twitch_viewers_count, info.viewerCount, info.viewerCount))
        }
    }
    Button(
        onClick = {
            twitchViewModel.updateChannelInfoForStream(
                settings = uiState,
                title = uiState.twitchStreamTitle,
                category = uiState.twitchStreamCategory,
            )
        },
        enabled = !twitchState.saving &&
            (uiState.twitchStreamTitle.isNotBlank() || uiState.twitchStreamCategory.isNotBlank()),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(
                if (twitchState.saving) R.string.twitch_saving else R.string.twitch_update_channel,
            ),
        )
    }
    val twitchErrorText = if (twitchState.errorRes != 0) {
        stringResource(twitchState.errorRes, *twitchState.formatArgs.toTypedArray())
    } else {
        twitchState.error
    }
    twitchErrorText?.let { error ->
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StreamMultiSettings(uiState: AppSettings, viewModel: SettingsViewModel) {
    // Multi-Streaming: optionales zweites Ziel (parallel zum primären)
    Text(
        text = stringResource(R.string.multi_streaming_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StreamUrlField(uiState.secondaryStreamUrl, R.string.secondary_url_label,
        StreamConfigValidator.validate(uiState.streamUrl, uiState.streamKey, uiState.streamUseTls,
            uiState.secondaryStreamUrl, uiState.secondaryStreamKey, uiState.secondaryStreamUseTls)
            .filter { it.severity == ConfigIssueSeverity.ERROR && it.prefixRes != 0 },
        "stream_secondary_url") { value ->
        viewModel.updateStreamDraft { it.copy(secondaryStreamUrl = value) }
    }
    OutlinedTextField(
        value = uiState.secondaryStreamKey,
        onValueChange = { value -> viewModel.updateStreamDraft { it.copy(secondaryStreamKey = value) } },
        label = { Text(stringResource(R.string.secondary_key_label)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.stream_tls_label), modifier = Modifier.weight(1f))
        Switch(
            checked = uiState.secondaryStreamUseTls,
            onCheckedChange = { value -> viewModel.updateStreamDraft { it.copy(secondaryStreamUseTls = value) } },
        )
    }
}

@Composable
private fun StreamObsSettings(uiState: AppSettings, viewModel: SettingsViewModel) {
    // OBS-Einstellungen
    Text(stringResource(R.string.obs_section_title), style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = uiState.obsHost,
        onValueChange = { value -> viewModel.updateStreamDraft { it.copy(obsHost = value) } },
        label = { Text(stringResource(R.string.obs_host_label)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = uiState.obsPort,
        onValueChange = { value -> viewModel.updateStreamDraft { it.copy(obsPort = value) } },
        label = { Text(stringResource(R.string.obs_port_label)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = uiState.obsPassword,
        onValueChange = { value -> viewModel.updateStreamDraft { it.copy(obsPassword = value) } },
        label = { Text(stringResource(R.string.obs_password_label)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.obs_tls_label), modifier = Modifier.weight(1f))
        Switch(
            checked = uiState.obsUseTls,
            onCheckedChange = { value -> viewModel.updateStreamDraft { it.copy(obsUseTls = value) } },
        )
    }
}

@Composable
private fun StreamUrlField(
    value: String,
    @androidx.annotation.StringRes label: Int,
    errors: List<StreamConfigIssue>,
    tag: String,
    onValueChange: (String) -> Unit,
) {
    val error = errors.takeIf { value.isNotEmpty() }?.firstOrNull()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { issue ->
            { Text(stringResource(issue.messageRes, *issue.formatArgs.toTypedArray())) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
