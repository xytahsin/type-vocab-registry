package com.tahsin.vocabregistry.domain

import com.tahsin.vocabregistry.data.model.*
import kotlin.math.max
import kotlin.math.min

object Readiness {
    /**
     * Continuous IELTS band 4.0–9.0 (un-rounded), from:
     *   production accuracy (40%), collocation (22%), coverage (18%),
     *   writing self-rating (20%).
     * Writing only pulls weight once the learner has actually rated essays
     * (writingStrength > 0); until then its share is redistributed to vocab,
     * so the band stays honest and never inflates from an empty writing record.
     */
    fun precise(words: List<Word>, axes: Map<Int, Map<Axis, AxisState>>, writingStrength: Double = 0.0): Double {
        val rel = words.filter { it.tier <= 2 }
        if (rel.isEmpty()) return 4.0
        var pSum = 0.0; var pN = 0; var cSum = 0.0; var cN = 0; var covered = 0
        for (w in rel) {
            val a = axes[w.id] ?: continue
            a[Axis.P]?.takeIf { it.lastReviewEpoch > 0 }?.let { pSum += it.stability; pN++ }
            a[Axis.C]?.takeIf { it.lastReviewEpoch > 0 }?.let { cSum += it.stability; cN++ }
            val rOk = a[Axis.R]?.status in setOf(AxisStatus.REVIEW, AxisStatus.MASTERED)
            val pOk = a[Axis.P]?.status in setOf(AxisStatus.REVIEW, AxisStatus.MASTERED)
            if (rOk && pOk) covered++
        }
        val pAcc = if (pN > 0) pSum / pN else 0.0
        val cAcc = if (cN > 0) cSum / cN else 0.0
        val cov  = covered.toDouble() / rel.size
        val ws = writingStrength.coerceIn(0.0, 1.0)
        val raw: Double = if (ws > 0.0) {
            0.40 * pAcc + 0.22 * cAcc + 0.18 * cov + 0.20 * ws
        } else {
            // no writing data yet — keep original vocab-only weighting
            0.45 * pAcc + 0.30 * cAcc + 0.25 * cov
        }
        return max(4.0, min(9.0, 5.0 + raw * 3.5))
    }

    /** Rounded-to-0.5 band — the headline number shown to the learner. */
    fun band(words: List<Word>, axes: Map<Int, Map<Axis, AxisState>>, writingStrength: Double = 0.0): Double {
        return Math.round(precise(words, axes, writingStrength) * 2.0) / 2.0
    }

    /** How full the ring is between the current 0.5 band and the next one (0..1). */
    fun ringFraction(words: List<Word>, axes: Map<Int, Map<Axis, AxisState>>, writingStrength: Double = 0.0): Float {
        val p = precise(words, axes, writingStrength)
        val floorHalf = Math.floor(p * 2.0) / 2.0
        return ((p - floorHalf) / 0.5).coerceIn(0.0, 1.0).toFloat()
    }
}
