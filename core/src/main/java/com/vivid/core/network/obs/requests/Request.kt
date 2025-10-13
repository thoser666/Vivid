package com.vivid.core.network.obs.requests

interface Request {
    fun toRequestWithId(requestId: String): RequestWithId {
        // This is a placeholder implementation. Specific requests should override this.
        return RequestWithId(
            op = 6, // Default OpCode for requests
            d = RequestData(
                requestType = this::class.java.simpleName,
                requestId = requestId,
                requestData = this
            )
        )
    }
}

data class RequestWithId(
    val op: Int,
    val d: RequestData
)

data class RequestData(
    val requestType: String,
    val requestId: String,
    val requestData: Request?
)