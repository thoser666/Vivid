package com.vivid.feature.chat.bot

/**
 * Kategorisierter Obszönitäts-/Hass-Rede-Filter, basierend auf:
 * - GamerSafer/word-blocklist (Racism/Sexism/Swearing/Harassment)
 * - Twitch AutoMod Kategorien (Discrimination, Sexual, Hostility, Profanity)
 * - YouTube Ad Guidelines (Strong/Moderate profanity)
 */
enum class ProfanityCategory(val displayName: String) {
    SLURS("Hassrede / Schimpfwörter"),
    SEXUAL("Sexuell explizit"),
    HOSTILITY("Aggression / Bedrohung"),
    PROFANITY("Allgemeine Vulgärsprache");

    companion object {
        fun fromName(name: String?): ProfanityCategory? =
            entries.find { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Statische Wortlisten und Regex-Patterns pro Kategorie.
 * Die Patterns erkennen 1337-Speak / Verschleierung mit Word-Boundaries.
 */
object ProfanityWordList {

    // ── SLURS ─────────────────────────────────────────────────────────────

    private val slursWords: Set<String> = setOf(
        "nigger", "nigga", "negro", "darkie",
        "chink", "gook", "spic", "spick", "wetback", "beaner",
        "kike", "heeb",
        "cracker", "honky", "whitey",
        "fag", "faggot", "faggit", "queer", "tranny", "trannie",
        "dyke", "shemale",
        "retard", "retarded",
        "kafir", "towelhead", "raghead",
    )

    private val slursPatterns: List<Regex> = listOf(
        Regex("""\bn[i!1]+gg[e3r]*[a@]*\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bf[a@]+gg[o0]*t\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\br[e3]+t[a@]+rd\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\btr[a@]+nn[yie]+\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsp[i!1]+ck?\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bch[i!1]+nk\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bk[i!1]+k[e3]\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bw[e3]+t[b@]+[a@]+ck\w*\b""", RegexOption.IGNORE_CASE),
    )

    // ── SEXUAL ────────────────────────────────────────────────────────────

    private val sexualWords: Set<String> = setOf(
        "penis", "vagina", "clitoris", "orgasm",
        "masturbate", "masturbation",
        "handjob", "blowjob", "rimjob", "titjob",
        "cumshot", "dildo", "vibrator", "fleshlight",
        "porn", "porno", "pornography", "hentai",
        "thot", "thottie",
    )

    private val sexualPatterns: List<Regex> = listOf(
        Regex("""\bp[e3]+n[i!1]+s\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bv[a@]+g[i!1]+n[a@]+\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbl[o0][w*]+j[o0]*b\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bh[a@]+ndj[o0]*b\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\br[i!1]+mj[o0]*b\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bt[i!1]+tj[o0]*b\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bc[u*]+msh[o0]+t\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bd[i!1]+ld[o0]\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bp[o0]+r[n]+[o0]?\w*\b""", RegexOption.IGNORE_CASE),
    )

    // ── HOSTILITY ─────────────────────────────────────────────────────────

    private val hostilityWords: Set<String> = setOf(
        "kill yourself", "kys", "commit suicide",
        "i will kill", "i'll kill",
        "shoot yourself", "hang yourself",
        "go die", "hope you die",
        "terrorist", "bomb threat",
        "swat", "dox", "doxx",
    )

    private val hostilityPatterns: List<Regex> = listOf(
        Regex("""\bk[i!1]+ll\s+y[o0]+ur?self\b""", RegexOption.IGNORE_CASE),
        Regex("""\bs[h\$]+[o0]+[o0]+t\s+y[o0]+ur?self\b""", RegexOption.IGNORE_CASE),
        Regex("""\bh[a@]+ng\s+y[o0]+ur?self\b""", RegexOption.IGNORE_CASE),
        Regex("""\bg[o0]\s+d[i!1]+e\b""", RegexOption.IGNORE_CASE),
        Regex("""\bh[o0]+p[e3]\s+y[o0]+u\s+d[i!1]+e\b""", RegexOption.IGNORE_CASE),
        Regex("""\bi['']?ll\s+k[i!1]+ll\s+y[o0]+u\b""", RegexOption.IGNORE_CASE),
        Regex("""\bt[e3]+rr[o0]+r[i!1]+st\b""", RegexOption.IGNORE_CASE),
    )

    // ── PROFANITY ─────────────────────────────────────────────────────────

    private val profanityWords: Set<String> = setOf(
        "fuck", "shit", "ass", "bitch", "damn", "dick",
        "cock", "cunt", "bastard", "whore", "slut", "crap",
        "douche", "piss", "prick", "tits", "boobs", "anus",
        "bollocks", "wanker", "tosser", "arse",
    )

    private val profanityPatterns: List<Regex> = listOf(
        Regex("""\bfu+[ck]+k*\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsh[i!1]+t\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\b[a@4]ss\b""", RegexOption.IGNORE_CASE),
        Regex("""\bb[i!1]+tch\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bd[i!1]+ck\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bc[o0*]+ck\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bc[u*_]+nt\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bb[a@4]+st[a@4]+rd\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwh[o0#]+re\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsl[u*]+t\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcr[a*]+p\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bp[i!1]+ss\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpr[i!1]+ck\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bt[i!1]+ts\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bb[o0]+bs\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bd[o0]+uch\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bw[a@]+nk\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bt[o0]+ss\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\ba[r*]+s[e3]*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bb[o0]+ll\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\bd[a@]+mn\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\ba[n@]+us\w*\b""", RegexOption.IGNORE_CASE),
    )

    // ── Maps (müssen NACH den private val deklariert werden) ──────────────

    val wordsByCategory: Map<ProfanityCategory, Set<String>> = mapOf(
        ProfanityCategory.SLURS to slursWords,
        ProfanityCategory.SEXUAL to sexualWords,
        ProfanityCategory.HOSTILITY to hostilityWords,
        ProfanityCategory.PROFANITY to profanityWords,
    )

    val patternsByCategory: Map<ProfanityCategory, List<Regex>> = mapOf(
        ProfanityCategory.SLURS to slursPatterns,
        ProfanityCategory.SEXUAL to sexualPatterns,
        ProfanityCategory.HOSTILITY to hostilityPatterns,
        ProfanityCategory.PROFANITY to profanityPatterns,
    )
}
