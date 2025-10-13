package com.vivid.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RegistrationRequest(
    val username: String,
    val email: String,
    val passwordHash: String
)