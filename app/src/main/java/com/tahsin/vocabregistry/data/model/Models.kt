package com.tahsin.vocabregistry.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class WordJson(
    val i: Int, val w: String, val p: String, val t: Int, val h: String,
    val d: String, val e: String, val c: List<String> = emptyList(),
    val s: List<String> = emptyList(), val x: String? = null,
)

@Entity(tableName = "words")
data class Word(
    @PrimaryKey val id: Int,
    val word: String,
    val pos: String,
    val tier: Int,
    val theme: String,
    val definition: String,
    val example: String,
    val collocations: String,   // pipe-joined
    val synonyms: String,       // semicolon-joined
    val confusable: String?,
) {
    val collocationList get() = collocations.split('|').map { it.trim() }.filter { it.isNotEmpty() }
    val synonymList get() = synonyms.split(';').map { it.trim() }.filter { it.isNotEmpty() }
}

enum class Axis(val growth: Double, val weight: Double, val label: String) {
    R(1.0, 1.0, "Recognition"),
    P(0.7, 1.6, "Production"),
    C(0.7, 1.5, "Collocation"),
    G(0.85, 1.1, "Register");
}

enum class AxisStatus { NEW, LEARNING, REVIEW, MASTERED, LAPSED }

@Entity(tableName = "axis_states", primaryKeys = ["wordId", "axis"])
data class AxisState(
    val wordId: Int,
    val axis: Axis,
    val intervalDays: Double = 0.0,
    val ease: Double = 2.5,
    val reps: Int = 0,
    val stability: Double = 0.0,
    val lastReviewEpoch: Long = 0L,     // 0 = never
    val dueEpoch: Long = 0L,
    val status: AxisStatus = AxisStatus.NEW,
)

@Entity(tableName = "review_log")
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Int,
    val axis: Axis,
    val epoch: Long,
    val quality: Int,           // 0-5
    val response: String,
    val errorTags: String,      // comma-joined
    val provisional: Boolean = false,
)

@Entity(tableName = "pending_grades")
data class PendingGrade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Int,
    val axis: Axis,
    val response: String,
    val epoch: Long,
    val provisionalQ: Int,
)
