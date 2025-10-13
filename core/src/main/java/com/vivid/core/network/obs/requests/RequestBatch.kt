package com.vivid.core.network.obs.requests

data class RequestBatch(
    val requests: List<Any>,
    val haltOnFailure: Boolean,
    val executionType: Int,
) : Request
