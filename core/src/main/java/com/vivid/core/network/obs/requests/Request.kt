package com.vivid.core.network.obs.requests

import com.google.gson.annotations.SerializedName

interface Request {
    // Die toRequestWithId-Methode erwartet jetzt den RequestType
    fun toRequestWithId(requestId: String, requestType: RequestType): RequestWithId {
        return RequestWithId(
            op = 6, // OpCode 6 für Request
            d = RequestData(
                requestType = requestType.name, // <-- WIR BENUTZEN JETZT DEN NAMEN DES ENUMS
                requestId = requestId,
                requestData = this
            )
        )
    }
}

data class RequestWithId(
    @SerializedName("op") val op: Int,
    @SerializedName("d") val d: RequestData
)

data class RequestData(
    @SerializedName("requestType") val requestType: String, // <-- WICHTIG: Typ ist jetzt String
    @SerializedName("requestId") val requestId: String,
    @SerializedName("requestData") val requestData: Any?
)