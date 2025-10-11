// ObsQrCodeData.kt
package com.vivid.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ObsQrCodeData(
    val host: String,
    val port: Int,
    val password: String,
)
