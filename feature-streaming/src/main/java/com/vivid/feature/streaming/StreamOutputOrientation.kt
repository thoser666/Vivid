package com.vivid.feature.streaming

import com.vivid.core.data.EncoderCapabilities

/** Camera buffers remain native landscape; codec support is checked against the encoded axes. */
internal fun orientedEncoderCapabilities(capabilities: EncoderCapabilities, portrait: Boolean): EncoderCapabilities =
    if (!portrait) capabilities else object : EncoderCapabilities {
        override fun supports(mime: String, width: Int, height: Int, fps: Int): Boolean =
            capabilities.supports(mime, height, width, fps)
    }
