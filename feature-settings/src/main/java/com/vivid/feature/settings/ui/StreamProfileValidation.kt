package com.vivid.feature.settings.ui

import com.vivid.core.data.StreamProfile
import com.vivid.feature.streaming.ConfigIssueSeverity
import com.vivid.feature.streaming.StreamConfigIssue
import com.vivid.feature.streaming.StreamConfigValidator

/** The same blocking checks used by the Go-Live coordinator, including the optional target. */
internal fun streamDraftErrors(profile: StreamProfile): List<StreamConfigIssue> =
    StreamConfigValidator.validate(profile.streamUrl, profile.streamKey, profile.streamUseTls,
        profile.secondaryStreamUrl, profile.secondaryStreamKey, profile.secondaryStreamUseTls)
        .filter { it.severity == ConfigIssueSeverity.ERROR }
