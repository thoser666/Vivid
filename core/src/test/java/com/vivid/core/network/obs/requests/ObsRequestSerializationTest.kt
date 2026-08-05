package com.vivid.core.network.obs.requests

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObsRequestSerializationTest {

    private val gson = Gson()

    @Test
    fun `request is wrapped with op code 6 and the enum name as request type`() {
        val wrapped = GetVersion().toRequestWithId("1", RequestType.GetVersion)

        assertEquals(6, wrapped.op)
        assertEquals("GetVersion", wrapped.d.requestType)
        assertEquals("1", wrapped.d.requestId)
        assertTrue(wrapped.d.requestData is GetVersion)
    }

    @Test
    fun `request serializes to the expected json envelope`() {
        val wrapped = GetVersion().toRequestWithId("1", RequestType.GetVersion)

        val jsonString = gson.toJson(wrapped)

        assertTrue(jsonString.contains("\"op\":6"))
        assertTrue(jsonString.contains("\"requestType\":\"GetVersion\""))
        assertTrue(jsonString.contains("\"requestId\":\"1\""))
        assertTrue(jsonString.contains("\"requestData\":{}"))
    }

    @Test
    fun `request batch serializes with its settings`() {
        val batch = RequestBatch(
            requests = listOf(GetVersion()),
            haltOnFailure = false,
            executionType = 1,
        )

        val jsonString = gson.toJson(batch)

        assertTrue(jsonString.contains("\"haltOnFailure\":false"))
        assertTrue(jsonString.contains("\"executionType\":1"))
        assertTrue(jsonString.contains("\"requests\":"))
    }

    @Test
    fun `request type name is stable`() {
        assertEquals("GetVersion", RequestType.GetVersion.name)
        assertEquals(listOf("GetVersion"), RequestType.entries.map { it.name })
    }
}
