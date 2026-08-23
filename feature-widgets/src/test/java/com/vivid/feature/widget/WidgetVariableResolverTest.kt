package com.vivid.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetVariableResolverTest {

    @Test
    fun `resolve replaces known variables`() {
        val template = "{time} | {speed}"
        val values = mapOf("time" to "14:05:32", "speed" to "52.3 km/h")
        assertEquals("14:05:32 | 52.3 km/h", WidgetVariableResolver.resolve(template, values))
    }

    @Test
    fun `resolve leaves unknown variables unchanged`() {
        val template = "{time} | {unknown}"
        val values = mapOf("time" to "14:05:32")
        assertEquals("14:05:32 | {unknown}", WidgetVariableResolver.resolve(template, values))
    }

    @Test
    fun `resolve handles empty template`() {
        assertEquals("", WidgetVariableResolver.resolve("", mapOf("time" to "14:05:32")))
    }

    @Test
    fun `resolve handles template with no variables`() {
        assertEquals("no variables here", WidgetVariableResolver.resolve("no variables here", emptyMap()))
    }

    @Test
    fun `resolve handles multiple same variables`() {
        val template = "{time} / {time}"
        val values = mapOf("time" to "14:05:32")
        assertEquals("14:05:32 / 14:05:32", WidgetVariableResolver.resolve(template, values))
    }

    @Test
    fun `resolve handles all variable types`() {
        val template = "{time} {date} {speed} {altitude} {lat} {lon}"
        val values = mapOf(
            "time" to "14:05:32",
            "date" to "17.08.2026",
            "speed" to "52.3 km/h",
            "altitude" to "120 m",
            "lat" to "52.52",
            "lon" to "13.405",
        )
        assertEquals("14:05:32 17.08.2026 52.3 km/h 120 m 52.52 13.405", WidgetVariableResolver.resolve(template, values))
    }

    @Test
    fun `currentValues builds correct map`() {
        val values = WidgetVariableResolver.currentValues(
            time = "14:05:32",
            date = "17.08.2026",
            speed = "52.3 km/h",
            altitude = "120 m",
            latitude = 52.52,
            longitude = 13.405,
        )
        assertEquals("14:05:32", values["time"])
        assertEquals("17.08.2026", values["date"])
        assertEquals("52.3 km/h", values["speed"])
        assertEquals("120 m", values["altitude"])
        assertEquals("52.52", values["lat"])
        assertEquals("13.405", values["lon"])
    }

    @Test
    fun `resolve with empty values leaves all variables unchanged`() {
        val template = "{time} {speed}"
        assertEquals("{time} {speed}", WidgetVariableResolver.resolve(template, emptyMap()))
    }
}
