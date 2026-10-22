package com.tahsin.vocabregistry.domain

import com.tahsin.vocabregistry.data.model.ReviewLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayCount(val date: LocalDate, val count: Int)
data class WeekStat(val label: String, val reviews: Int, val accuracy: Float)

data class StatsData(
    val totalReviews: Int = 0,
    val daysStudied: Int = 0,
    val overallAccuracy: Float = 0f,   // fraction with quality >= 3
    val heatmap: List<DayCount> = emptyList(),  // oldest..newest, includes empty days
    val weeks: List<WeekStat> = emptyList(),     // oldest..newest
    val busiestDay: Int = 0,           // max reviews in a single day (heatmap scaling)
)

/** Pure aggregation of review history — no Android dependencies. */
object Stats {
    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    fun compute(
        logs: List<ReviewLog>,
        today: LocalDate,
        zone: ZoneId,
        heatmapDays: Int = 112,   // 16 weeks
        weeksBack: Int = 8,
    ): StatsData {
        val perDay = HashMap<LocalDate, Int>()
        val perDayPass = HashMap<LocalDate, Int>()
        var pass = 0
        for (l in logs) {
            val d = Instant.ofEpochMilli(l.epoch).atZone(zone).toLocalDate()
            perDay[d] = (perDay[d] ?: 0) + 1
            if (l.quality >= 3) {
                pass++
                perDayPass[d] = (perDayPass[d] ?: 0) + 1
            }
        }
        val total = logs.size
        val acc = if (total == 0) 0f else pass.toFloat() / total

        val heat = (0 until heatmapDays).map { i ->
            val d = today.minusDays((heatmapDays - 1 - i).toLong())
            DayCount(d, perDay[d] ?: 0)
        }
        val busiest = heat.maxOfOrNull { it.count } ?: 0

        val weeks = (0 until weeksBack).map { w ->
            val end = today.minusDays(((weeksBack - 1 - w) * 7).toLong())
            val start = end.minusDays(6)
            var rv = 0
            var pv = 0
            var d = start
            while (!d.isAfter(end)) {
                rv += perDay[d] ?: 0
                pv += perDayPass[d] ?: 0
                d = d.plusDays(1)
            }
            val a = if (rv == 0) 0f else pv.toFloat() / rv
            val label = "${MONTHS[start.monthValue - 1]} ${start.dayOfMonth}"
            WeekStat(label, rv, a)
        }

        return StatsData(
            totalReviews = total,
            daysStudied = perDay.size,
            overallAccuracy = acc,
            heatmap = heat,
            weeks = weeks,
            busiestDay = busiest,
        )
    }
}
