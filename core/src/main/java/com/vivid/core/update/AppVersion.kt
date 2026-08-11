package com.vivid.core.update

/**
 * Release-Kanal, abgeleitet aus dem versionName-Suffix (siehe RELEASE.md „Versionsstrategie").
 *
 * Die [rank]-Reihenfolge bildet die Cross-Track-Update-Regeln aus RELEASE.md ab:
 * nightly → nightly/alpha/beta/rc/stable (steigend), aber alpha → nightly ist ein Downgrade.
 */
enum class ReleaseChannel(val rank: Int, val label: String) {
    NIGHTLY(0, "nightly"),
    ALPHA(1, "alpha"),
    BETA(2, "beta"),
    RC(3, "rc"),
    STABLE(4, "stable"),
}

/**
 * Semantische Version nach dem Vivid-Schema:
 * `X.Y.Z`, `X.Y.Z-alpha`, `X.Y.Z-beta`, `X.Y.Z-nightly.N` (siehe RELEASE.md).
 *
 * Vergleichsreihenfolge: (major, minor, patch) → [ReleaseChannel.rank] → [buildNumber].
 * Damit gelten exakt die Cross-Track-Regeln aus RELEASE.md:
 * - nightly.93 < nightly.95 (gleicher Kanal, höhere Run-Nummer)
 * - nightly.93 < 0.2.0-alpha (höherer Kanal = Update-Pfad laut RELEASE.md)
 * - 0.2.0-alpha > 0.2.0-nightly.95 (alpha → nightly wäre ein Downgrade)
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val channel: ReleaseChannel = ReleaseChannel.STABLE,
    val buildNumber: Int = 0,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val base = compareValuesBy(
            this, other,
            { it.major }, { it.minor }, { it.patch },
        )
        if (base != 0) return base
        val channelCmp = channel.rank.compareTo(other.channel.rank)
        if (channelCmp != 0) return channelCmp
        return buildNumber.compareTo(other.buildNumber)
    }

    override fun toString(): String {
        val base = "$major.$minor.$patch"
        return when (channel) {
            ReleaseChannel.STABLE -> base
            else -> if (buildNumber > 0) "$base-${channel.label}.$buildNumber" else "$base-${channel.label}"
        }
    }

    companion object {
        /**
         * Versionsmuster innerhalb eines Release-Namens/Tags, z. B.:
         * - `0.2.0-nightly.93` (aus „Vivid nightly (0.2.0-nightly.93)“)
         * - `v0.2.0-alpha` (aus dem Tag „v0.2.0-alpha“)
         * - `1.0` / `0.2.0` (stabile Versionen)
         */
        private val VERSION_REGEX = Regex(
            """(?<![0-9A-Za-z.])v?(\d+)\.(\d+)(?:\.(\d+))?(?:-(nightly|alpha|beta|rc)(?:\.(\d+))?)?(?![0-9A-Za-z.])""",
        )

        /**
         * Extrahiert die erste Version aus [input] (Release-Name oder Tag).
         * Gibt `null` zurück, wenn kein gültiges Muster enthalten ist (z. B. nightly-Tags
         * wie `nightly-20260811-0428` ohne Punkte).
         */
        fun parse(input: String): AppVersion? {
            val match = VERSION_REGEX.find(input) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: 0
            val channel = when (match.groupValues[4]) {
                "nightly" -> ReleaseChannel.NIGHTLY
                "alpha" -> ReleaseChannel.ALPHA
                "beta" -> ReleaseChannel.BETA
                "rc" -> ReleaseChannel.RC
                else -> ReleaseChannel.STABLE
            }
            val buildNumber = match.groupValues[5].toIntOrNull() ?: 0
            return AppVersion(major, minor, patch, channel, buildNumber)
        }
    }
}
