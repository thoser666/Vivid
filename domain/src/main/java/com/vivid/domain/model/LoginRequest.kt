package com.vivid.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val email: String = "",
    val password: String,
)
