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
    val soundEveryCorrect: Boolean = true,
    val soundPrecise: Boolean = true,
    val rich: Map<Int, RichExtras> = emptyMap(),
    // --- gamification ---
    val preciseBand: Double = 4.0,
    val ringFrac: Float = 0f,
    val xp: Long = 0L,
    val essays: Int = 0,
    val bestWriting: Double = 0.0,
    val writingToday: Int = 0,
    val questsDoneCount: Int = 0,
    val questAllDoneToday: Boolean = false,
    val tierMastered: Map<Int, Int> = emptyMap(),
    val tierTotals: Map<Int, Int> = emptyMap(),
    val bandUp: Boolean = false,
    val comeback: Boolean = false,
    val reminderOn: Boolean = true,
    val reminderHour: Int = 19,
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

        // ---- gamification computations ----
        val today = LocalDate.now().toString()
        val writingStrength = p[Keys.WRITING_STRENGTH] ?: 0.0
        val prof = repo.proficiency()
        val band = Readiness.band(words, axes, writingStrength)
        val precise = Readiness.precise(words, axes, writingStrength)
        val ringFrac = Readiness.ringFraction(words, axes, writingStrength)
        val newToday = p[Keys.NEW_TODAY] ?: 0
        val writingToday = if (p[Keys.WRITING_TODAY_DATE] == today) (p[Keys.WRITING_TODAY] ?: 0) else 0

        // band-up + comeback bookkeeping (writes flags, then we re-read)
        val prevBand = p[Keys.LAST_BAND_SEEN] ?: 0.0
        val bandUp = prevBand > 0.0 && band > prevBand
        repo.edit { e ->
            e[Keys.LAST_BAND_SEEN] = band
            val wasDemoted = e[Keys.WAS_DEMOTED] ?: false
            if (prof.demoted) e[Keys.WAS_DEMOTED] = true
            else if (wasDemoted) e[Keys.COMEBACK] = true
        }
        // daily quests: 15 reviews, 3 new words, 1 essay
        val rGoal = 15; val nGoal = 3; val wGoal = 1
        repo.settleQuests(reviewsToday, newToday, writingToday, rGoal, nGoal, wGoal)
        val questAllDoneToday = reviewsToday >= rGoal && newToday >= nGoal && writingToday >= wGoal

        // tier mastery (for trophies)
        val tierTotals = words.groupBy { it.tier }.mapValues { (_, v) -> v.size }
        val masteredIds = axes.filter { (_, m) -> Axis.entries.all { k -> m[k]?.status == AxisStatus.MASTERED } }.keys
        val masteredWords = words.filter { it.id in masteredIds }
        val tierMastered = masteredWords.groupBy { it.tier }.mapValues { (_, v) -> v.size }

        val p2 = repo.prefs()   // fresh read after the edits above
        val dueNow = repo.db.axes().due(now).size
        val wotd = repo.wordOfDay()
        val streakNow = p2[Keys.STREAK] ?: 0
        _ui.value = UiSnapshot(
            loading = false,
            calibrated = p2[Keys.CALIBRATED] ?: false,
            words = words, axes = axes,
            dueCount = dueNow,
            band = band,
            streak = streakNow, longest = p2[Keys.LONGEST] ?: 0,
            freezes = p2[Keys.FREEZES] ?: 2,
            examDate = exam,
            daysToExam = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(exam)),
            proficiency = prof,
            newToday = newToday,
            academicMode = p2[Keys.ACADEMIC] ?: false,
            capOverride = p2[Keys.CAP_OVERRIDE],
            history = (p2[Keys.HISTORY] ?: "").split(";").filter { it.contains(":") }
                .map { val (d, b) = it.split(":"); d to b.toDouble() },
            apiKeySet = !keyCache.isNullOrBlank(),
            mastered = mastered,
            themeMode = runCatching { ThemeMode.valueOf(p2[Keys.THEME_MODE] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            tier6 = p2[Keys.TIER6] ?: false, tier7 = p2[Keys.TIER7] ?: false, tier8 = p2[Keys.TIER8] ?: false,
            wordOfDay = wotd,
            reviewsToday = reviewsToday,
            soundEveryCorrect = p2[Keys.SOUND_CORRECT] ?: true,
            soundPrecise = p2[Keys.SOUND_PRECISE] ?: true,
            rich = repo.richExtras(),
            preciseBand = precise,
            ringFrac = ringFrac,
            xp = p2[Keys.XP] ?: 0L,
            essays = p2[Keys.ESSAYS] ?: 0,
            bestWriting = p2[Keys.BEST_WRITING] ?: 0.0,
            writingToday = writingToday,
            questsDoneCount = p2[Keys.QUESTS_DONE] ?: 0,
            questAllDoneToday = questAllDoneToday,
            tierMastered = tierMastered,
            tierTotals = tierTotals,
            bandUp = bandUp,
            comeback = p2[Keys.COMEBACK] ?: false,
            reminderOn = p2[Keys.REMINDER_ON] ?: true,
            reminderHour = p2[Keys.REMINDER_HOUR] ?: 19,
        )
        // keep the home-screen widget in sync with the values we just computed
        val bandStr = if (band > 0.0) "%.1f".format(band) else "\u2014"
        com.tahsin.vocabregistry.widget.LexiconWidget.push(
            getApplication(), dueNow, streakNow, bandStr, wotd?.word ?: "\u2014",
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

    fun finishSession(xpEarned: Long = 0L) = viewModelScope.launch {
        val s = _ui.value
        val ws = repo.prefs()[Keys.WRITING_STRENGTH] ?: 0.0
        repo.creditSession(Readiness.band(s.words, repo.axesMap(), ws))
        if (xpEarned > 0L) repo.addXp(xpEarned)
        refresh()
    }

    /** Self-rated writing: feeds the readiness band (writing component) and hero XP. */
    fun creditWriting(selfBand: Double) = viewModelScope.launch {
        repo.creditWriting(selfBand)
        refresh()
    }

    fun creditXp(amount: Long) = viewModelScope.launch {
        repo.addXp(amount); refresh()
    }

    /** Aggregate review history for the insights panel. */
    suspend fun loadStats(): StatsData {
        val logs = repo.db.logs().recent(2000)
        return Stats.compute(logs, LocalDate.now(), java.time.ZoneId.systemDefault())
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
    fun setReminder(on: Boolean, hour: Int) = viewModelScope.launch {
        repo.edit { it[Keys.REMINDER_ON] = on; it[Keys.REMINDER_HOUR] = hour }
        com.tahsin.vocabregistry.notify.Reminders.sync(getApplication())
        refresh()
    }
    fun setThemeMode(m: ThemeMode) = viewModelScope.launch { repo.edit { it[Keys.THEME_MODE] = m.name }; refresh() }
    fun setTier6(b: Boolean) = viewModelScope.launch { repo.edit { it[Keys.TIER6] = b }; refresh() }
    fun setTier7(b: Boolean) = viewModelScope.launch { repo.edit { it[Keys.TIER7] = b }; refresh() }
    fun setTier8(b: Boolean) = viewModelScope.launch { repo.edit { it[Keys.TIER8] = b }; refresh() }
    fun setSoundCorrect(b: Boolean) = viewModelScope.launch { repo.edit { it[Keys.SOUND_CORRECT] = b }; refresh() }
    fun setSoundPrecise(b: Boolean) = viewModelScope.launch { repo.edit { it[Keys.SOUND_PRECISE] = b }; refresh() }
    fun setApiKey(k: String) = viewModelScope.launch {
        keyCache = k.ifBlank { null }
        repo.edit { it[Keys.API_KEY] = k }; refresh()
    }
}
