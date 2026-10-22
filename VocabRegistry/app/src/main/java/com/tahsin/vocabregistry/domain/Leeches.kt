package com.tahsin.vocabregistry.domain

import com.tahsin.vocabregistry.data.model.ReviewLog

/** A word that keeps being failed: [fails] poor reviews out of [reviews] total. */
data class LeechStat(val wordId: Int, val fails: Int, val reviews: Int)

object Leeches {
    const val DEFAULT_THRESHOLD = 6

    /**
     * Derives leeches purely from the review log (no schema change): a word becomes a
     * leech once it has accumulated [threshold] or more failed reviews (quality < 3).
     * Returned worst-first.
     */
    fun detect(logs: List<ReviewLog>, threshold: Int = DEFAULT_THRESHOLD): List<LeechStat> {
        val fails = HashMap<Int, Int>()
        val total = HashMap<Int, Int>()
        for (l in logs) {
            total[l.wordId] = (total[l.wordId] ?: 0) + 1
            if (l.quality < 3) fails[l.wordId] = (fails[l.wordId] ?: 0) + 1
        }
        return fails.asSequence()
            .filter { it.value >= threshold }
            .map { (id, f) -> LeechStat(id, f, total[id] ?: f) }
            .sortedByDescending { it.fails }
            .toList()
    }
}
