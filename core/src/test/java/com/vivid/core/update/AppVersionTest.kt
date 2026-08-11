package com.vivid.core.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppVersionTest {

    // --- Parsing ---

    @Test
    fun `parses plain stable version`() {
        assertEquals(AppVersion(0, 2, 0), AppVersion.parse("0.2.0"))
    }

    @Test
    fun `parses major-minor version without patch`() {
        assertEquals(AppVersion(1, 0, 0), AppVersion.parse("1.0"))
    }

    @Test
    fun `parses nightly with build number`() {
        assertEquals(AppVersion(0, 2, 0, ReleaseChannel.NIGHTLY, 93), AppVersion.parse("0.2.0-nightly.93"))
    }

    @Test
    fun `parses alpha without build number`() {
        assertEquals(AppVersion(0, 2, 0, ReleaseChannel.ALPHA), AppVersion.parse("0.2.0-alpha"))
    }

    @Test
    fun `parses beta with build number`() {
        assertEquals(AppVersion(0, 2, 0, ReleaseChannel.BETA, 2), AppVersion.parse("0.2.0-beta.2"))
    }

    @Test
    fun `parses version inside the release name`() {
        assertEquals(
            AppVersion(0, 2, 0, ReleaseChannel.NIGHTLY, 93),
            AppVersion.parse("Vivid nightly (0.2.0-nightly.93)"),
        )
    }

    @Test
    fun `parses v-prefixed tag`() {
        assertEquals(AppVersion(0, 2, 0, ReleaseChannel.ALPHA), AppVersion.parse("v0.2.0-alpha"))
    }

    @Test
    fun `returns null for a tag without version pattern`() {
        assertNull(AppVersion.parse("nightly-20260811-0428"))
    }

    @Test
    fun `returns null for garbage`() {
        assertNull(AppVersion.parse("not a version"))
    }

    // --- Vergleich (RELEASE.md Cross-Track-Regeln) ---

    @Test
    fun `higher nightly build number is newer`() {
        assertTrue(AppVersion.parse("0.2.0-nightly.93")!! < AppVersion.parse("0.2.0-nightly.95")!!)
    }

    @Test
    fun `alpha is newer than nightly at the same base version`() {
        assertTrue(AppVersion.parse("0.2.0-nightly.95")!! < AppVersion.parse("0.2.0-alpha")!!)
    }

    @Test
    fun `stable is newer than alpha at the same base version`() {
        assertTrue(AppVersion.parse("0.2.0-alpha")!! < AppVersion.parse("0.2.0")!!)
    }

    @Test
    fun `higher base version beats channel rank`() {
        assertTrue(AppVersion.parse("0.3.0-nightly.1")!! > AppVersion.parse("0.2.0-alpha")!!)
    }

    @Test
    fun `equal versions compare equal`() {
        assertEquals(0, AppVersion.parse("0.2.0-nightly.93")!!.compareTo(AppVersion.parse("0.2.0-nightly.93")!!))
    }

    // --- toString ---

    @Test
    fun `toString roundtrips the version name`() {
        assertEquals("0.2.0-nightly.93", AppVersion.parse("0.2.0-nightly.93").toString())
        assertEquals("0.2.0-alpha", AppVersion.parse("0.2.0-alpha").toString())
        assertEquals("0.2.0", AppVersion.parse("0.2.0").toString())
    }
}
