package com.tahsin.vocabregistry.domain

import androidx.compose.ui.graphics.Color

/* ============================================================
 *  HEROIC BAND IDENTITY
 *  Each half-band looks more heroic than the previous one:
 *  escalating name, colour, and glow. Drives the readiness ring.
 * ============================================================ */
data class BandHero(
    val title: String,
    val ring: Color,      // the filled arc colour
    val core: Color,      // the big number colour
    val glow: Float,      // 0..1 — how much aura the ring radiates (rises with band)
    val blurb: String,
)

object Heroics {
    /** Identity for a (rounded-to-0.5) band number. */
    fun hero(band: Double): BandHero = when {
        band >= 9.0 -> BandHero("Luminary", Color(0xFFFFF4C2), Color(0xFFFFFFFF), 1.0f,
            "Native-grade control. The ceiling.")
        band >= 8.5 -> BandHero("Virtuoso", Color(0xFFFFE27A), Color(0xFFFFF0B8), 0.92f,
            "Effortless range and precision.")
        band >= 8.0 -> BandHero("Maestro", Color(0xFFF4D03F), Color(0xFFFFE680), 0.82f,
            "Wide vocabulary, rare slips.")
        band >= 7.5 -> BandHero("Expert", Color(0xFFE8C547), Color(0xFFF4D769), 0.70f,
            "Flexible, fluent, exam-ready.")
        band >= 7.0 -> BandHero("Adept", Color(0xFFE0B83C), Color(0xFFEFD06A), 0.58f,
            "The target band is in hand.")
        band >= 6.5 -> BandHero("Scholar", Color(0xFFC9CDD6), Color(0xFFE6E9F0), 0.46f,
            "Solid, consistent, climbing.")
        band >= 6.0 -> BandHero("Journeyman", Color(0xFFAEB4C2), Color(0xFFD7DCE6), 0.36f,
            "A working command of English.")
        band >= 5.5 -> BandHero("Apprentice", Color(0xFFC08A4A), Color(0xFFD9A867), 0.26f,
            "Foundations are setting fast.")
        band >= 5.0 -> BandHero("Initiate", Color(0xFFB07D44), Color(0xFFCF9A5C), 0.18f,
            "The ascent has begun.")
        else        -> BandHero("Aspirant", Color(0xFF8A93A8), Color(0xFFB6BECC), 0.10f,
            "Every expert started here.")
    }

    /** The next milestone number above the current rounded band (for the ring's goal). */
    fun nextNumber(band: Double): Double = (band + 0.5).coerceAtMost(9.0)
}

/* ============================================================
 *  XP + HERO LEVELS  (always-forward; never demoted)
 *  Captures *everything* — reviews, mastery, writing, quests —
 *  so progress always visibly moves even when the band is flat.
 * ============================================================ */
enum class HeroRank(val title: String, val minXp: Long, val color: Color) {
    NOVICE("Novice Scribe", 0, Color(0xFF9AA6BF)),
    PAGE("Page of Letters", 250, Color(0xFFB07D44)),
    SQUIRE("Word-Squire", 700, Color(0xFFC08A4A)),
    KNIGHT("Lexical Knight", 1600, Color(0xFFAEB4C2)),
    CHAMPION("Champion of Diction", 3200, Color(0xFFC9CDD6)),
    SAGE("Sage of Syntax", 6000, Color(0xFFE0B83C)),
    LUMINARY("Luminary of Letters", 10000, Color(0xFFE8C547)),
    IMMORTAL("Immortal Wordsmith", 16000, Color(0xFFFFE27A));

    companion object {
        fun forXp(xp: Long): HeroRank = entries.last { xp >= it.minXp }
        fun next(rank: HeroRank): HeroRank? = entries.getOrNull(rank.ordinal + 1)
    }
}

data class HeroProgress(
    val rank: HeroRank,
    val xp: Long,
    val intoRank: Long,      // xp earned since this rank began
    val rankSpan: Long,      // xp width of this rank (0 = max rank)
    val frac: Float,         // 0..1 progress to next rank
) {
    val nextTitle: String get() = HeroRank.next(rank)?.title ?: "Max rank reached"
}

object HeroXp {
    fun progress(xp: Long): HeroProgress {
        val rank = HeroRank.forXp(xp)
        val next = HeroRank.next(rank)
        val into = xp - rank.minXp
        val span = if (next != null) next.minXp - rank.minXp else 0L
        val frac = if (span > 0L) (into.toFloat() / span).coerceIn(0f, 1f) else 1f
        return HeroProgress(rank, xp, into, span, frac)
    }

    /** XP for finishing a session: rewards volume and quality. */
    fun forSession(items: Int, avgQuality: Double): Long {
        val base = items.toLong() * 4L
        val bonus = when {
            avgQuality >= 4.0 -> 20L
            avgQuality >= 3.0 -> 10L
            else -> 4L
        }
        return base + bonus
    }

    /** XP for self-rating a piece of writing against the rubric. */
    fun forWriting(selfBand: Double): Long = (12 + (selfBand * 6)).toLong()

    const val QUEST_ALL_BONUS: Long = 40L
    const val NEW_WORD: Long = 3L
}
