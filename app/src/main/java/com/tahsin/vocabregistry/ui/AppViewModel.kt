package com.tahsin.vocabregistry.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tahsin.vocabregistry.data.Keys
import com.tahsin.vocabregistry.data.VocabRepository
import com.tahsin.vocabregistry.data.model.*
import com.tahsin.vocabregistry.domain.*
import com.tahsin.vocabregistry.grading.*
import com.tahsin.vocabregistry.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class UiSnapshot(
    val loading: Boolean = true,
    val calibrated: Boolean = false,
    val words: List<Word> = emptyList(),
    val axes: Map<Int, Map<Axis, AxisState>> = emptyMap(),
    val dueCount: Int = 0,
    val band: Double = 4.0,
    val streak: Int = 0, val longest: Int = 0, val freezes: Int = 2,
    val examDate: String = "2027-02-15",
    val daysToExam: Long = 0,
    val proficiency: ProficiencySnapshot =
        ProficiencySnapshot(ProficiencyLevel.DEVELOPING, 0.0, 0.0, 0.0, false),
    val newToday: Int = 0,
    val academicMode: Boolean = false,
    val capOverride: Int? = null,
    val history: List<Pair<String, Double>> = emptyList(),
    val apiKeySet: Boolean = false,
    val mastered: Int = 0,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val tier6: Boolean = false, val tier7: Boolean = false, val tier8: Boolean = false,
    val wordOfDay: Word? = null,
    val reviewsToday: Int = 0,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = VocabRepository.get(app)
    private var keyCache: String? = null
    val grader = Grader { keyCache }

    private val _ui = MutableStateFlow(UiSnapshot())
    val ui = _ui.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        repo.seedIfNeeded()
        repo.dailyHousekeeping()
        val p = repo.prefs()
        keyCache = p[Keys.API_KEY]
        val words = repo.db.words().all()
        val axes = repo.axesMap()
        val now = System.currentTimeMillis()
        val exam = p[Keys.EXAM_DATE] ?: "2027-02-15"
        val mastered = axes.count { (_, m) ->
            Axis.entries.all { k -> m[k]?.status == AxisStatus.MASTERED }
        }
        val startToday = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val reviewsToday = repo.db.logs().recent(1500).count { it.epoch >= startToday }
        _ui.value = UiSnapshot(
            loading = false,
            calibrated = p[Keys.CALIBRATED] ?: false,
            words = words, axes = axes,
            dueCount = repo.db.axes().due(now).size,
            band = Readiness.band(words, axes),
            streak = p[Keys.STREAK] ?: 0, longest = p[Keys.LONGEST] ?: 0,
            freezes = p[Keys.FREEZES] ?: 2,
            examDate = exam,
            daysToExam = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(exam)),
            proficiency = repo.proficiency(),
            newToday = p[Keys.NEW_TODAY] ?: 0,
            academicMode = p[Keys.ACADEMIC] ?: false,
            capOverride = p[Keys.CAP_OVERRIDE],
            history = (p[Keys.HISTORY] ?: "").split(";").filter { it.contains(":") }
                .map { val (d, b) = it.split(":"); d to b.toDouble() },
            apiKeySet = !keyCache.isNullOrBlank(),
            mastered = mastered,
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            tier6 = p[Keys.TIER6] ?: false, tier7 = p[Keys.TIER7] ?: false, tier8 = p[Keys.TIER8] ?: false,
            wordOfDay = repo.wordOfDay(),
            reviewsToday = reviewsToday,
        )
    }

    fun composeSession(mode: SessionMode): List<SessionCard> {
        val s = _ui.value
        return SessionComposer.compose(
            mode, s.words, s.axes, System.currentTimeMillis(),
            s.daysToExam.toDouble(), s.proficiency.level, s.newToday,
            s.academicMode, s.capOverride, s.tier6, s.tier7, s.tier8,
        )
    }

    /** Apply a graded review. The proficiency EMAs fold in every result, so the
     *  grader band, interval modifier, intake, and distractors all shift with performance. */
    fun applyReview(wordId: Int, axis: Axis, q: Int, response: String, tags: List<String>, provisional: Boolean) =
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val current = repo.db.axes().forWord(wordId).find { it.axis == axis }
                ?: AxisState(wordId, axis)
            val ivMod = _ui.value.proficiency.level.intervalModifier
            repo.db.axes().upsert(Sm2Engine.update(
                current.copy(status = if (current.status == AxisStatus.NEW) AxisStatus.LEARNING else current.status),
                q, now, ivMod))
            repo.recordReview(ReviewLog(0, wordId, axis, now, q, response.take(240),
                tags.joinToString(","), provisional))
            // ACTIVATION: first successful recognition wakes the productive axes,
            // staggered so production (P) arrives first, then collocation, then register.
            if (axis == Axis.R && q >= 3) {
                val siblings = repo.db.axes().forWord(wordId)
                val day = Sm2Engine.DAY_MS
                for (sib in siblings) {
                    if (sib.axis != Axis.R && sib.status == AxisStatus.NEW) {
                        val stagger = when (sib.axis) { Axis.P -> 0L; Axis.C -> day; else -> 2 * day }
                        repo.db.axes().upsert(sib.copy(
                            status = AxisStatus.LEARNING, intervalDays = 1.0,
                            dueEpoch = now + stagger))
                    }
                }
            }
        }

    fun introduceWord(wordId: Int) = viewModelScope.launch {
        val existing = repo.db.axes().forWord(wordId)
        if (existing.isEmpty()) {
            repo.db.axes().upsertAll(Axis.entries.map { AxisState(wordId, it) })
            repo.bumpNewToday()
        }
    }

    fun finishSession() = viewModelScope.launch {
        val s = _ui.value
        repo.creditSession(Readiness.band(s.words, repo.axesMap()))
        refresh()
    }

    fun seedCalibration(results: List<Pair<Int, Boolean>>) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val day = Sm2Engine.DAY_MS
        for ((id, ok) in results) {
            val states = if (ok) listOf(
                AxisState(id, Axis.R, 7.0, 2.5, 2, 0.85, now, now + 7 * day, AxisStatus.REVIEW),
                AxisState(id, Axis.P, 1.5, 2.5, 1, 0.5, now, now + (1.5 * day).toLong(), AxisStatus.LEARNING),
                AxisState(id, Axis.C, 1.5, 2.5, 1, 0.5, now, now + (1.5 * day).toLong(), AxisStatus.LEARNING),
                AxisState(id, Axis.G, 3.0, 2.5, 1, 0.6, now, now + 3 * day, AxisStatus.LEARNING),
            ) else listOf(
                AxisState(id, Axis.R, 1.0, 2.5, 0, 0.2, now, now, AxisStatus.LEARNING),
                AxisState(id, Axis.P), AxisState(id, Axis.C), AxisState(id, Axis.G),
            )
            repo.db.axes().upsertAll(states)
        }
        repo.edit { it[Keys.CALIBRATED] = true }
        refresh()
    }

    fun setExamDate(d: String) = viewModelScope.launch { repo.edit { it[Keys.EXAM_DATE] = d }; refresh() }
    fun setAcademic(b: Boolean) = viewModelScope.launch { repo.edit { it[Keys.ACADEMIC] = b }; refresh() }
    fun setThemeMode(m: ThemeMode) = viewModelScope.launch { repo.edit { it[Keys.THEME_MODE] = m.name }; refresh() }
    fun setTier6(b: Boolean) = viewModelScope.launch { repo.edit { it[Keys.TIER6] = b }; refresh() }
    fun setTier7(b: Boolean) = viewModelScope.launch { repo.edit { it[Keys.TIER7] = b }; refresh() }
    fun setTier8(b: Boolean) = viewModelScope.launch { repo.edit { it[Keys.TIER8] = b }; refresh() }
    fun setApiKey(k: String) = viewModelScope.launch {
        keyCache = k.ifBlank { null }
        repo.edit { it[Keys.API_KEY] = k }; refresh()
    }
}
