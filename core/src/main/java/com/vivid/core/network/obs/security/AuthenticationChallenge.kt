package com.vivid.core.network.obs.security

data class AuthenticationChallenge(
    val op: Int,
    val d: Data,
) {
    data class Data(
        val rpcVersion: Int,
        val authentication: Authentication?,
    )

    data class Authentication(
        val challenge: String,
        val salt: String,
    )
}
