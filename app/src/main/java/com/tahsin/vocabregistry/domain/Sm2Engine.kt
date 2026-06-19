package com.tahsin.vocabregistry.domain

import com.tahsin.vocabregistry.data.model.Axis
import com.tahsin.vocabregistry.data.model.AxisState
import com.tahsin.vocabregistry.data.model.AxisStatus
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object Sm2Engine {
    const val DAY_MS = 86_400_000L
    private const val DECAY = 0.05

    /**
     * Modified SM-2 per axis. [intervalModifier] comes from the ProficiencyTracker —
     * the schedule itself adapts as the learner gets better or worse.
     */
    fun update(state: AxisState, q: Int, now: Long, intervalModifier: Double): AxisState {
        val wasReview = state.status == AxisStatus.REVIEW || state.status == AxisStatus.MASTERED
        var iv = state.intervalDays
        var reps = state.reps
        var status: AxisStatus
        if (q < 3) {
            reps = 0; iv = 1.0
            status = if (wasReview) AxisStatus.LAPSED else AxisStatus.LEARNING
        } else {
            reps += 1
            val base = when (reps) {
                1 -> 1.0
                2 -> 4.0
                else -> state.intervalDays * state.ease
            }
            iv = max(1.0, base * state.axis.growth * intervalModifier)
            status = when {
                iv >= 21 && q >= 4 -> AxisStatus.MASTERED
                iv >= 6 -> AxisStatus.REVIEW
                else -> AxisStatus.LEARNING
            }
        }
        val ease = max(1.3, state.ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))
        val stability = min(1.0, max(0.0, q / 5.0))
        return state.copy(
            intervalDays = iv, ease = ease, reps = reps, stability = stability,
            lastReviewEpoch = now, dueEpoch = now + (iv * DAY_MS).toLong(), status = status,
        )
    }

    /** Stability decay for overdue items; harsh resets for badly neglected productive axes. */
    fun decay(state: AxisState, now: Long): AxisState {
        if (state.dueEpoch == 0L || state.dueEpoch >= now) return state
        val overdueDays = (now - state.dueEpoch).toDouble() / DAY_MS
        var s = state.copy(stability = state.stability * exp(-DECAY * overdueDays))
        if (overdueDays >= 3 * max(state.intervalDays, 1.0)) {
            s = if (state.axis == Axis.P || state.axis == Axis.C)
                s.copy(intervalDays = 1.0, reps = 0, ease = max(1.3, s.ease - 0.15), status = AxisStatus.LAPSED)
            else s.copy(ease = max(1.3, s.ease - 0.15), status = AxisStatus.LAPSED)
        }
        return s
    }
}
