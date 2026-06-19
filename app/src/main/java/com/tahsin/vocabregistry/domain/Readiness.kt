package com.tahsin.vocabregistry.domain

import com.tahsin.vocabregistry.data.model.*
import kotlin.math.max
import kotlin.math.min

object Readiness {
    /** band 4.0–9.0 from production accuracy (45%), collocation accuracy (30%), coverage (25%). */
    fun band(words: List<Word>, axes: Map<Int, Map<Axis, AxisState>>): Double {
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
        val raw = 0.45 * (if (pN > 0) pSum / pN else 0.0) +
                  0.30 * (if (cN > 0) cSum / cN else 0.0) +
                  0.25 * (covered.toDouble() / rel.size)
        val band = max(4.0, min(9.0, 5.0 + raw * 3.5))
        return Math.round(band * 2.0) / 2.0
    }
}
