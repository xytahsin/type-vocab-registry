package com.tahsin.vocabregistry.domain

import com.tahsin.vocabregistry.data.model.Axis
import com.tahsin.vocabregistry.data.model.ReviewLog

/**
 * THE ADAPTIVE CORE.
 * Tracks an exponential moving average of review quality per axis and converts it
 * into a proficiency level that re-tunes the whole system as the learner improves
 * or declines:
 *
 *   - graderBand        → injected into the LLM grading prompt: the same sentence is
 *                          graded against a HIGHER IELTS band as the learner advances.
 *   - intervalModifier  → globally stretches/compresses SM-2 intervals.
 *   - newCapDelta       → adjusts daily new-word intake.
 *   - distractorMode    → cloze distractors move from random → same-theme → near-synonym.
 *
 * Asymmetric by design: promotion requires sustained evidence (slow EMA, alpha 0.06),
 * demotion is immediate if the last 20 production reviews average below 2.5
 * ("fast down, slow up") — so a slump tightens the system right away.
 */
enum class ProficiencyLevel(
    val graderBand: Double,
    val intervalModifier: Double,
    val newCapDelta: Int,
    val title: String,
) {
    DEVELOPING(5.5, 0.85, -2, "Developing"),
    COMPETENT(6.5, 1.0, 0, "Competent"),
    PROFICIENT(7.5, 1.1, 2, "Proficient"),
    ADVANCED(8.5, 1.2, 4, "Advanced");
}

data class ProficiencySnapshot(
    val level: ProficiencyLevel,
    val emaP: Double, val emaC: Double, val emaR: Double,
    val demoted: Boolean,
)

object ProficiencyTracker {
    private const val ALPHA = 0.06
    private const val DEMOTE_WINDOW = 20
    private const val DEMOTE_THRESHOLD = 2.5

    fun fold(prevEmaP: Double, prevEmaC: Double, prevEmaR: Double, log: ReviewLog): Triple<Double, Double, Double> {
        val q = log.quality / 5.0
        return when (log.axis) {
            Axis.P, Axis.G -> Triple(ema(prevEmaP, q), prevEmaC, prevEmaR)
            Axis.C -> Triple(prevEmaP, ema(prevEmaC, q), prevEmaR)
            Axis.R -> Triple(prevEmaP, prevEmaC, ema(prevEmaR, q))
        }
    }
    private fun ema(prev: Double, q: Double) = if (prev <= 0.0) q else ALPHA * q + (1 - ALPHA) * prev

    fun snapshot(emaP: Double, emaC: Double, emaR: Double, recentProduction: List<ReviewLog>): ProficiencySnapshot {
        // Production drives strictness; collocation and recognition stabilise the estimate.
        val composite = 0.6 * emaP + 0.25 * emaC + 0.15 * emaR
        var level = when {
            composite < 0.45 -> ProficiencyLevel.DEVELOPING
            composite < 0.65 -> ProficiencyLevel.COMPETENT
            composite < 0.82 -> ProficiencyLevel.PROFICIENT
            else -> ProficiencyLevel.ADVANCED
        }
        var demoted = false
        if (recentProduction.size >= DEMOTE_WINDOW) {
            val avg = recentProduction.take(DEMOTE_WINDOW).map { it.quality }.average()
            if (avg < DEMOTE_THRESHOLD && level.ordinal > 0) {
                level = ProficiencyLevel.entries[level.ordinal - 1]
                demoted = true
            }
        }
        return ProficiencySnapshot(level, emaP, emaC, emaR, demoted)
    }

    enum class DistractorMode { RANDOM_POS, SAME_THEME, NEAR_SYNONYM }
    fun distractorMode(level: ProficiencyLevel) = when (level) {
        ProficiencyLevel.DEVELOPING -> DistractorMode.RANDOM_POS
        ProficiencyLevel.COMPETENT -> DistractorMode.SAME_THEME
        else -> DistractorMode.NEAR_SYNONYM
    }
}
