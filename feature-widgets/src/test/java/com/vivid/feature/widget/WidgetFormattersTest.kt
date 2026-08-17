package com.vivid.feature.widget

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WidgetFormattersTest {

    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private val previousDefaultZone: TimeZone = TimeZone.getDefault()

    /**
     * 2026-08-17 14:05:32 Europe/Berlin — mit der JVM-eigenen TZDB berechnet,
     * damit der Test unabhängig von der lokalen tzdata-Version deterministisch ist.
     */
    private fun epoch(): Long =
        ZonedDateTime.of(2026, 8, 17, 14, 5, 32, 0, ZoneId.of("Europe/Berlin"))
            .toInstant().toEpochMilli()

    @Before
    fun setUp() {
        // Deterministische Zone für Zeit-/Datums-Tests.
        TimeZone.setDefault(zone)
    }

    // Zone nach jedem Test zurücksetzen.
    @org.junit.After
    fun tearDown() {
        TimeZone.setDefault(previousDefaultZone)
    }

    @Test
    fun `formatTime renders 24h time in the given zone`() {
        assertEquals("14:05:32", WidgetFormatters.formatTime(epoch(), zone))
    }

    @Test
    fun `formatDate renders dd MM yyyy in the given zone`() {
        assertEquals("17.08.2026", WidgetFormatters.formatDate(epoch(), zone))
    }

    @Test
    fun `formatCoordinates uses dot separator and cardinal directions`() {
        assertEquals("52.5200° N, 13.4050° O", WidgetFormatters.formatCoordinates(52.52, 13.405))
    }

    @Test
    fun `formatCoordinates handles southern and western hemispheres`() {
        assertEquals("33.8688° S, 151.2093° O", WidgetFormatters.formatCoordinates(-33.8688, 151.2093))
        assertEquals("52.5200° N, 13.4050° W", WidgetFormatters.formatCoordinates(52.52, -13.405))
    }

    @Test
    fun `formatSpeed converts meters per second to kmh with german decimal`() {
        assertEquals("36,0 km/h", WidgetFormatters.formatSpeed(10f))
        assertEquals("0,0 km/h", WidgetFormatters.formatSpeed(0f))
    }

    @Test
    fun `formatSpeed returns dash for missing or invalid values`() {
        assertEquals("–", WidgetFormatters.formatSpeed(null))
        assertEquals("–", WidgetFormatters.formatSpeed(-1f))
    }
}
