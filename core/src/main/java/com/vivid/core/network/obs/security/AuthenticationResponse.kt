package com.vivid.core.network.obs.security

data class AuthenticationResponse(
    val op: Int,
    val d: Data,
) {
    data class Data(
        val rpcVersion: Int,
        val authentication: String,
        val eventSubscriptions: Int,
    )
}
