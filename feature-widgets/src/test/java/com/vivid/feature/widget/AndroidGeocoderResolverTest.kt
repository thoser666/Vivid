package com.vivid.feature.widget

import android.content.Context
import android.location.Address
import android.location.Geocoder
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowGeocoder

/**
 * Robolectric-Tests für den Android-Geocoder-Adapter: ShadowGeocoder liefert
 * gesteuerte [Address]es — Happy Path, Fehlbehandlung und „kein Backend“.
 * Der Resolver bekommt per [AndroidGeocoderResolver.geocoderFactory] eine feste
 * Geocoder-Instanz, deren Shadow geteilt gesteuert wird.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class AndroidGeocoderResolverTest {

    private val sharedGeocoder = Geocoder(ApplicationProvider.getApplicationContext<Context>(), Locale.US)
    private val resolver = AndroidGeocoderResolver(ApplicationProvider.getApplicationContext()).apply {
        geocoderFactory = { sharedGeocoder }
    }

    @Before
    fun setUp() {
        // ShadowGeocoder: Default-Zustand je Test (isPresent ist statisch, Adressen instanzgebunden).
        ShadowGeocoder.setIsPresent(true)
        shadowOf(sharedGeocoder).setFromLocation(emptyList())
    }

    private fun address(
        road: String? = null,
        city: String? = null,
        adminArea: String? = null,
        country: String? = null,
    ): Address = Address(Locale.US).apply {
        thoroughfare = road
        locality = city
        this.adminArea = adminArea
        countryName = country
    }

    @Test
    fun `maps thoroughfare locality country`() = runTest {
        shadowOf(sharedGeocoder).setFromLocation(
            listOf(address(road = "Kurfürstendamm", city = "Berlin", country = "Germany")),
        )

        val result = resolver.placenames(52.52, 13.405)

        assertEquals(Placenames(road = "Kurfürstendamm", city = "Berlin", country = "Germany"), result)
    }

    @Test
    fun `falls back to adminArea when locality is missing`() = runTest {
        shadowOf(sharedGeocoder).setFromLocation(
            listOf(address(road = "Musterweg", adminArea = "Bavaria", country = "Germany")),
        )

        val result = resolver.placenames(48.13, 11.57)

        assertEquals("Bavaria", result?.city)
    }

    @Test
    fun `returns null when geocoder has no result`() = runTest {
        shadowOf(sharedGeocoder).setFromLocation(emptyList())

        assertNull(resolver.placenames(0.0, 0.0))
    }

    @Test
    fun `returns null when geocoder backend is not present`() = runTest {
        ShadowGeocoder.setIsPresent(false)

        assertNull(resolver.placenames(52.52, 13.405))
    }

    @Test
    fun `returns null on io error instead of crashing`() = runTest {
        shadowOf(sharedGeocoder).setErrorMessage("backend unavailable")

        assertNull(resolver.placenames(52.52, 13.405))
    }

    @Test
    fun `empty placename fields map to dash placeholders`() = runTest {
        shadowOf(sharedGeocoder).setFromLocation(listOf(address(country = "Germany")))

        val result = resolver.placenames(52.52, 13.405)

        val (road, city, country) = result!!.toDisplayValues()
        assertEquals("–", road)
        assertEquals("–", city)
        assertEquals("Germany", country)
    }
}
