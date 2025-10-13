package com.vivid.core.network.obs.requests

import com.google.gson.annotations.SerializedName

interface Request {
    fun toRequestWithId(requestId: String): RequestWithId {
        return RequestWithId(
            op = 6,
            d = RequestData(
                requestType = this::class.simpleName ?: "UnknownRequest",
                requestId = requestId,
                requestData = this,
            ),
        )
    }
}

data class RequestWithId(
    @SerializedName("op") val op: Int,
    @SerializedName("d") val d: RequestData
)

data class RequestData(
    @SerializedName("requestType") val requestType: String,
    @SerializedName("requestId") val requestId: String,
    @SerializedName("requestData") val requestData: Any? // Any? ist flexibler für Gson
)
