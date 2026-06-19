package com.tahsin.vocabregistry.domain

import com.tahsin.vocabregistry.data.model.*
import kotlin.math.ceil
import kotlin.math.max

enum class SessionMode(val budget: Int) { SPRINT(12), DEEP(30), COMMUTE(20) }
enum class CardKind { DUE, NEW, WEAK }
data class SessionCard(val wordId: Int, val axis: Axis, val kind: CardKind, val meetFirst: Boolean = false)

object SessionComposer {

    fun tierWeight(tier: Int, daysToExam: Double): Double =
        if (daysToExam > 0) mapOf(1 to 2.0, 2 to 2.0, 3 to 0.8, 4 to 1.0)[tier] ?: 1.0
        else mapOf(1 to 1.0, 2 to 1.0, 3 to 0.5, 4 to 2.0)[tier] ?: 1.0

    fun newDailyCap(daysToExam: Double, level: ProficiencyLevel, override: Int?): Int {
        if (override != null) return override
        val base = when { daysToExam > 120 -> 9; daysToExam > 0 -> 5; else -> 3 }
        return max(2, base + level.newCapDelta)   // adaptive intake
    }

    fun unlockedTiers(
        words: List<Word>, axes: Map<Int, Map<Axis, AxisState>>, academicMode: Boolean,
        bcs: Boolean = false, bank: Boolean = false, scholar: Boolean = false,
    ): List<Int> {
        fun pct(tier: Int): Double {
            val tw = words.filter { it.tier == tier }
            if (tw.isEmpty()) return 0.0
            val ok = tw.count { w ->
                val a = axes[w.id] ?: return@count false
                listOf(Axis.R, Axis.P).all { k ->
                    val st = a[k]?.status
                    st == AxisStatus.REVIEW || st == AxisStatus.MASTERED
                }
            }
            return ok.toDouble() / tw.size
        }
        val tiers = mutableListOf(1)
        if (pct(1) >= 0.75) tiers += 2
        if (tiers.contains(2) && pct(2) >= 0.70) tiers += 3
        if (academicMode) tiers += 4
        tiers += 5            // IBA exam words: always available
        if (bcs) tiers += 6
        if (bank) tiers += 7
        if (scholar) tiers += 8
        return tiers
    }

    fun compose(
        mode: SessionMode,
        words: List<Word>,
        axes: Map<Int, Map<Axis, AxisState>>,
        now: Long,
        daysToExam: Double,
        level: ProficiencyLevel,
        newUsedToday: Int,
        academicMode: Boolean,
        capOverride: Int?,
        bcs: Boolean = false, bank: Boolean = false, scholar: Boolean = false,
    ): List<SessionCard> {
        val budget = mode.budget
        val recogOnly = mode == SessionMode.COMMUTE
        val wordById = words.associateBy { it.id }

        // ---- due pool, priority-sorted ----
        data class DueItem(val wordId: Int, val axis: Axis, val score: Double)
        val due = axes.values.flatMap { it.values }
            .filter { it.dueEpoch in 1..now && it.status != AxisStatus.NEW }
            .filter { !recogOnly || it.axis == Axis.R }
            .mapNotNull { st ->
                val w = wordById[st.wordId] ?: return@mapNotNull null
                val overdue = max(0.1, (now - st.dueEpoch).toDouble() / Sm2Engine.DAY_MS)
                DueItem(st.wordId, st.axis,
                    overdue * tierWeight(w.tier, daysToExam) * (1 + (1 - st.stability)) * st.axis.weight)
            }.sortedByDescending { it.score }

        val cards = mutableListOf<SessionCard>()
        val used = mutableSetOf<String>()
        fun push(id: Int, axis: Axis, kind: CardKind, meet: Boolean = false): Boolean {
            val key = "$id${axis.name}"
            if (!used.add(key)) return false
            cards += SessionCard(id, axis, kind, meet); return true
        }

        val dueQuota = if (mode == SessionMode.SPRINT) budget else ceil(budget * 0.6).toInt()
        for (d in due) { if (cards.size >= dueQuota) break; push(d.wordId, d.axis, CardKind.DUE) }

        if (mode != SessionMode.SPRINT) {
            // ---- new words (25%, capped by adaptive daily intake) ----
            val cap = max(0, newDailyCap(daysToExam, level, capOverride) - newUsedToday)
            val newQuota = minOf(ceil(budget * 0.25).toInt(), cap)
            // round-robin across unlocked tiers so IELTS core and IBA words both surface
            val pools = unlockedTiers(words, axes, academicMode, bcs, bank, scholar).filter { it != 4 }
                .associateWith { t -> words.filter { it.tier == t && !axes.containsKey(it.id) }.toMutableList() }
            val order = pools.keys.toList()
            var added = 0
            var guard = 0
            while (added < newQuota && guard < 10000) {
                guard++
                var progressed = false
                for (t in order) {
                    if (added >= newQuota) break
                    val pool = pools[t]
                    if (pool != null && pool.isNotEmpty()) {
                        val w = pool.removeAt(0)
                        if (push(w.id, Axis.R, CardKind.NEW, meet = true)) added++
                        progressed = true
                    }
                }
                if (!progressed) break
            }
            // ---- weak-axis drills (15%) ----
            if (!recogOnly) {
                val weak = axes.entries.flatMap { (id, m) ->
                    val ivs = m.values.filter { it.lastReviewEpoch > 0 }.map { it.intervalDays }
                    if (ivs.size < 2) return@flatMap emptyList()
                    val maxIv = ivs.max()
                    m.values.filter {
                        it.lastReviewEpoch > 0 && it.intervalDays > 0 &&
                        maxIv / it.intervalDays >= 3 && (it.axis == Axis.P || it.axis == Axis.C)
                    }.map { Triple(id, it.axis, maxIv / it.intervalDays) }
                }.sortedByDescending { it.third }
                var wAdded = 0
                for ((id, axis, _) in weak) {
                    if (wAdded >= ceil(budget * 0.15).toInt() || cards.size >= budget) break
                    if (push(id, axis, CardKind.WEAK)) wAdded++
                }
            }
            for (d in due) { if (cards.size >= budget) break; push(d.wordId, d.axis, CardKind.DUE) }
        }

        // interleave: shuffle, keep same word non-adjacent
        cards.shuffle()
        for (i in 1 until cards.size) {
            if (cards[i].wordId == cards[i - 1].wordId) {
                val k = (i + 1 until cards.size).firstOrNull { cards[it].wordId != cards[i - 1].wordId }
                if (k != null) { val t = cards[i]; cards[i] = cards[k]; cards[k] = t }
            }
        }
        return cards.take(budget)
    }
}
