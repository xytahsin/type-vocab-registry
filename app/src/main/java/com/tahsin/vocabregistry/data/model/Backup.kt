package com.tahsin.vocabregistry.data.model

import kotlinx.serialization.Serializable

/** Portable snapshot of all progress — written to / read from a file the user controls. */
@Serializable
data class Backup(
    val version: Int = 1,
    val app: String = "Tahsincabs",
    val exportedAt: Long,
    val prefs: PrefsDTO,
    val axes: List<AxisDTO>,
    val logs: List<LogDTO>,
)

@Serializable
data class PrefsDTO(
    val examDate: String,
    val streak: Int, val longest: Int, val lastStudy: String?,
    val freezes: Int, val freezeMonth: String?,
    val newToday: Int, val newTodayDate: String?,
    val academic: Boolean, val capOverride: Int?, val calibrated: Boolean,
    val emaP: Double, val emaC: Double, val emaR: Double,
    val history: String,
    val themeMode: String = "DARK",
    val tier6: Boolean = false, val tier7: Boolean = false, val tier8: Boolean = false,
)

@Serializable
data class AxisDTO(
    val wordId: Int, val axis: String,
    val intervalDays: Double, val ease: Double, val reps: Int, val stability: Double,
    val lastReviewEpoch: Long, val dueEpoch: Long, val status: String,
)

@Serializable
data class LogDTO(
    val wordId: Int, val axis: String, val epoch: Long, val quality: Int,
    val response: String, val errorTags: String, val provisional: Boolean,
)
