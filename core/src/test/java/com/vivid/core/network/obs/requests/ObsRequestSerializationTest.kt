package com.vivid.core.network.obs.requests

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        assertEquals(
            listOf(
                "GetVersion",
                "GetInputList",
                "GetInputMute",
                "SetInputMute",
                "ToggleInputMute",
                "GetInputSettings",
                "SetInputSettings",
                "GetSceneList",
                "GetCurrentProgramScene",
                "SetCurrentProgramScene",
                "CreateScene",
                "CreateInput",
                "TakeSourceScreenshot",
            ),
            RequestType.entries.map { it.name },
        )
    }

    @Test
    fun `take source screenshot request drops null size fields and keeps format`() {
        val wrapped = TakeSourceScreenshot("Scene 1").toRequestWithId("3", RequestType.TakeSourceScreenshot)

        val jsonString = gson.toJson(wrapped)

        assertTrue(jsonString.contains("\"requestType\":\"TakeSourceScreenshot\""))
        assertTrue(jsonString.contains("\"sourceName\":\"Scene 1\""))
        assertTrue(jsonString.contains("\"imageFormat\":\"png\""))
        assertFalse(jsonString.contains("imageWidth"))
        assertFalse(jsonString.contains("imageHeight"))
    }

    @Test
    fun `take source screenshot serializes explicit size`() {
        val wrapped = TakeSourceScreenshot("Scene 1", imageWidth = 1920, imageHeight = 1080)
            .toRequestWithId("4", RequestType.TakeSourceScreenshot)

        val jsonString = gson.toJson(wrapped)

        assertTrue(jsonString.contains("\"imageWidth\":1920"))
        assertTrue(jsonString.contains("\"imageHeight\":1080"))
    }

    @Test
    fun `set input settings carries sync offset and overlay flag`() {
        val wrapped = SetInputSettings("Mic/Aux", mapOf("syncOffset" to 50_000_000L), overlay = false)
            .toRequestWithId("5", RequestType.SetInputSettings)

        val jsonString = gson.toJson(wrapped)

        assertTrue(jsonString.contains("\"requestType\":\"SetInputSettings\""))
        assertTrue(jsonString.contains("\"inputName\":\"Mic/Aux\""))
        assertTrue(jsonString.contains("\"syncOffset\":50000000"))
        assertTrue(jsonString.contains("\"overlay\":false"))
    }

    @Test
    fun `toggle mute request carries only the input name`() {
        val wrapped = ToggleInputMute("Desktop Audio").toRequestWithId("6", RequestType.ToggleInputMute)

        val jsonString = gson.toJson(wrapped)

        assertTrue(jsonString.contains("\"requestType\":\"ToggleInputMute\""))
        assertTrue(jsonString.contains("\"inputName\":\"Desktop Audio\""))
        assertFalse(jsonString.contains("inputMuted"))
    }

    @Test
    fun `create blackout input serializes color source kind and color`() {
        val wrapped = CreateInput(
            sceneName = "Vivid Blackout",
            inputName = "Blackout",
            inputKind = "color_source_v3",
            inputSettings = mapOf("color" to 0xFF000000L),
        ).toRequestWithId("7", RequestType.CreateInput)

        val jsonString = gson.toJson(wrapped)

        assertTrue(jsonString.contains("\"sceneName\":\"Vivid Blackout\""))
        assertTrue(jsonString.contains("\"inputKind\":\"color_source_v3\""))
        assertTrue(jsonString.contains("\"color\":4278190080"))
        assertTrue(jsonString.contains("\"sceneItemEnabled\":true"))
    }
}
