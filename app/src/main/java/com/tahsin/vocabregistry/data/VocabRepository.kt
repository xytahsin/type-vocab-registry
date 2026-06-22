package com.tahsin.vocabregistry.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.tahsin.vocabregistry.data.db.AppDatabase
import com.tahsin.vocabregistry.data.model.*
import com.tahsin.vocabregistry.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore("vocab_prefs")

object Keys {
    val EXAM_DATE = stringPreferencesKey("exam_date")
    val STREAK = intPreferencesKey("streak")
    val LONGEST = intPreferencesKey("longest")
    val LAST_STUDY = stringPreferencesKey("last_study")
    val FREEZES = intPreferencesKey("freezes")
    val FREEZE_MONTH = stringPreferencesKey("freeze_month")
    val NEW_TODAY = intPreferencesKey("new_today")
    val NEW_TODAY_DATE = stringPreferencesKey("new_today_date")
    val ACADEMIC = booleanPreferencesKey("academic")
    val CAP_OVERRIDE = intPreferencesKey("cap_override")
    val CALIBRATED = booleanPreferencesKey("calibrated")
    val API_KEY = stringPreferencesKey("api_key")
    val EMA_P = doublePreferencesKey("ema_p")
    val EMA_C = doublePreferencesKey("ema_c")
    val EMA_R = doublePreferencesKey("ema_r")
    val HISTORY = stringPreferencesKey("readiness_history")   // "date:band;date:band"
    val THEME_MODE = stringPreferencesKey("theme_mode")       // DARK | LIGHT | HIGH_CONTRAST
    val TIER6 = booleanPreferencesKey("tier6_bcs")
    val TIER7 = booleanPreferencesKey("tier7_bank")
    val TIER8 = booleanPreferencesKey("tier8_academic")
    val WOTD_DATE = stringPreferencesKey("wotd_date")
    val SOUND_CORRECT = booleanPreferencesKey("sound_correct")
    val SOUND_PRECISE = booleanPreferencesKey("sound_precise")
    // --- gamification (new; all default safely to 0/empty so existing installs are unaffected) ---
    val WRITING_STRENGTH = doublePreferencesKey("writing_strength")     // EMA 0..1 of self-rated writing
    val XP = longPreferencesKey("xp_total")                             // always-forward hero XP
    val ESSAYS = intPreferencesKey("essays_written")
    val BEST_WRITING = doublePreferencesKey("best_writing_band")
    val WRITING_TODAY = intPreferencesKey("writing_today")
    val WRITING_TODAY_DATE = stringPreferencesKey("writing_today_date")
    val LAST_BAND_SEEN = doublePreferencesKey("last_band_seen")
    val QUEST_DATE = stringPreferencesKey("quest_bonus_date")
    val QUESTS_DONE = intPreferencesKey("quests_all_done_count")
    val WAS_DEMOTED = booleanPreferencesKey("was_demoted")
    val COMEBACK = booleanPreferencesKey("comeback_earned")
    // --- reminders + home-screen widget ---
    val REMINDER_ON = booleanPreferencesKey("reminder_on")
    val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    val WIDGET_DUE = intPreferencesKey("widget_due")
    val WIDGET_STREAK = intPreferencesKey("widget_streak")
    val WIDGET_BAND = stringPreferencesKey("widget_band")
    val WIDGET_WOTD = stringPreferencesKey("widget_wotd")
}

class VocabRepository private constructor(private val ctx: Context) {
    val db: AppDatabase = Room.databaseBuilder(ctx, AppDatabase::class.java, "vocab.db").build()
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var richCache: Map<Int, RichExtras>? = null
    @Volatile private var writingCache: List<WritingJson>? = null

    suspend fun seedIfNeeded() {
        if (db.words().count() > 0) return
        val raw = ctx.assets.open("vocab.json").bufferedReader().use { it.readText() }
        val items = json.decodeFromString<List<WordJson>>(raw)
        db.words().insertAll(items.map {
            Word(it.i, it.w, it.p, it.t, it.h, it.d, it.e,
                it.c.joinToString("|"), it.s.joinToString(";"), it.x)
        })
    }

    /** Parse the bundled vocab.json once into per-word rich extras (examples/antonyms/idioms). */
    suspend fun richExtras(): Map<Int, RichExtras> {
        richCache?.let { return it }
        return try {
            val raw = ctx.assets.open("vocab.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<WordJson>>(raw).associate { wj ->
                wj.i to RichExtras(
                    examples = (listOf(wj.e) + wj.xs).map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                    antonyms = wj.an.map { it.trim() }.filter { it.isNotEmpty() },
                    idioms = wj.im.map { it.trim() }.filter { it.isNotEmpty() },
                )
            }.also { richCache = it }
        } catch (e: Exception) { emptyMap() }
    }

    suspend fun axesMap(): Map<Int, Map<Axis, AxisState>> =
        db.axes().all().groupBy { it.wordId }.mapValues { (_, v) -> v.associateBy { it.axis } }

    /** Parse the bundled writing.json once into the Task 2 library (prompts + dual-band samples). */
    suspend fun writingItems(): List<WritingJson> {
        writingCache?.let { return it }
        return try {
            val raw = ctx.assets.open("writing.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<WritingJson>>(raw).also { writingCache = it }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun applyDecayAll(now: Long) {
        val decayed = db.axes().all().map { Sm2Engine.decay(it, now) }
        db.axes().upsertAll(decayed)
    }

    // ---- proficiency (the adaptive state) ----
    suspend fun proficiency(): ProficiencySnapshot {
        val p = ctx.dataStore.data.first()
        val recentProd = db.logs().recentForAxis(Axis.P, 20)
        return ProficiencyTracker.snapshot(
            p[Keys.EMA_P] ?: 0.0, p[Keys.EMA_C] ?: 0.0, p[Keys.EMA_R] ?: 0.0, recentProd)
    }
    suspend fun recordReview(log: ReviewLog) {
        db.logs().insert(log); db.logs().trim()
        ctx.dataStore.edit { p ->
            val (np, nc, nr) = ProficiencyTracker.fold(
                p[Keys.EMA_P] ?: 0.0, p[Keys.EMA_C] ?: 0.0, p[Keys.EMA_R] ?: 0.0, log)
            p[Keys.EMA_P] = np; p[Keys.EMA_C] = nc; p[Keys.EMA_R] = nr
        }
    }

    // ---- prefs convenience ----
    val prefsFlow get() = ctx.dataStore.data
    suspend fun prefs() = ctx.dataStore.data.first()
    suspend fun edit(block: (MutablePreferences) -> Unit) = ctx.dataStore.edit(block)

    suspend fun bumpNewToday() {
        val today = LocalDate.now().toString()
        edit { p ->
            if (p[Keys.NEW_TODAY_DATE] != today) { p[Keys.NEW_TODAY_DATE] = today; p[Keys.NEW_TODAY] = 0 }
            p[Keys.NEW_TODAY] = (p[Keys.NEW_TODAY] ?: 0) + 1
        }
    }

    suspend fun creditSession(band: Double) {
        val today = LocalDate.now().toString()
        edit { p ->
            if (p[Keys.LAST_STUDY] != today) {
                val streak = (p[Keys.STREAK] ?: 0) + 1
                p[Keys.STREAK] = streak
                p[Keys.LONGEST] = maxOf(p[Keys.LONGEST] ?: 0, streak)
                p[Keys.LAST_STUDY] = today
            }
            val hist = (p[Keys.HISTORY] ?: "").split(";").filter { it.isNotBlank() }.toMutableList()
            if (hist.isNotEmpty() && hist.last().startsWith(today)) hist[hist.size - 1] = "$today:$band"
            else hist += "$today:$band"
            p[Keys.HISTORY] = hist.takeLast(200).joinToString(";")
        }
    }

    /** Deterministic word-of-the-day, drawn only from currently enabled tiers. */
    /** Award always-forward hero XP (never decreases). */
    suspend fun addXp(amount: Long) {
        if (amount <= 0L) return
        edit { p -> p[Keys.XP] = (p[Keys.XP] ?: 0L) + amount }
    }

    /** Record a self-rated piece of writing: updates the writing EMA (which feeds the band),
     *  bumps essay count / best band / today's writing quest, and awards XP. */
    suspend fun creditWriting(selfBand: Double) {
        val sb = selfBand.coerceIn(4.0, 9.0)
        val today = LocalDate.now().toString()
        edit { p ->
            // EMA of normalised band (4..9 -> 0..1); alpha 0.30 so a few essays move it but it stays stable
            val norm = ((sb - 4.0) / 5.0).coerceIn(0.0, 1.0)
            val prev = p[Keys.WRITING_STRENGTH] ?: 0.0
            p[Keys.WRITING_STRENGTH] = if (prev <= 0.0) norm else 0.30 * norm + 0.70 * prev
            p[Keys.ESSAYS] = (p[Keys.ESSAYS] ?: 0) + 1
            p[Keys.BEST_WRITING] = maxOf(p[Keys.BEST_WRITING] ?: 0.0, sb)
            if (p[Keys.WRITING_TODAY_DATE] != today) { p[Keys.WRITING_TODAY_DATE] = today; p[Keys.WRITING_TODAY] = 0 }
            p[Keys.WRITING_TODAY] = (p[Keys.WRITING_TODAY] ?: 0) + 1
            p[Keys.XP] = (p[Keys.XP] ?: 0L) + HeroXp.forWriting(sb)
        }
    }

    /** Daily-quest bookkeeping: award the all-complete bonus once per day. Returns nothing. */
    suspend fun settleQuests(reviewsToday: Int, newToday: Int, writingToday: Int,
                             rGoal: Int, nGoal: Int, wGoal: Int) {
        val today = LocalDate.now().toString()
        val allDone = reviewsToday >= rGoal && newToday >= nGoal && writingToday >= wGoal
        if (!allDone) return
        edit { p ->
            if (p[Keys.QUEST_DATE] != today) {
                p[Keys.QUEST_DATE] = today
                p[Keys.QUESTS_DONE] = (p[Keys.QUESTS_DONE] ?: 0) + 1
                p[Keys.XP] = (p[Keys.XP] ?: 0L) + HeroXp.QUEST_ALL_BONUS
            }
        }
    }

    suspend fun wordOfDay(): com.tahsin.vocabregistry.data.model.Word? {
        val words = db.words().all()
        val axes = axesMap()
        val p = ctx.dataStore.data.first()
        val unlocked = com.tahsin.vocabregistry.domain.SessionComposer.unlockedTiers(
            words, axes, p[Keys.ACADEMIC] ?: false,
            p[Keys.TIER6] ?: false, p[Keys.TIER7] ?: false, p[Keys.TIER8] ?: false,
        ).toSet()
        val pool = words.filter { it.tier in unlocked }.sortedBy { it.id }
        if (pool.isEmpty()) return null
        val idx = (java.time.LocalDate.now().toEpochDay().mod(pool.size.toLong())).toInt()
        return pool[idx]
    }

    suspend fun dailyHousekeeping() {
        val today = LocalDate.now()
        edit { p ->
            val month = today.toString().substring(0, 7)
            if (p[Keys.FREEZE_MONTH] != month) { p[Keys.FREEZE_MONTH] = month; p[Keys.FREEZES] = 2 }
            val last = p[Keys.LAST_STUDY]
            if (last != null) {
                val gap = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(last), today)
                if (gap == 2L && (p[Keys.FREEZES] ?: 0) > 0) p[Keys.FREEZES] = (p[Keys.FREEZES] ?: 0) - 1
                else if (gap > 2L) p[Keys.STREAK] = 0
            }
            if (p[Keys.NEW_TODAY_DATE] != today.toString()) { p[Keys.NEW_TODAY_DATE] = today.toString(); p[Keys.NEW_TODAY] = 0 }
        }
        applyDecayAll(System.currentTimeMillis())
    }

    // ---- portable backup / restore ----
    suspend fun exportJson(): String {
        val p = ctx.dataStore.data.first()
        val axes = db.axes().all().map {
            AxisDTO(it.wordId, it.axis.name, it.intervalDays, it.ease, it.reps, it.stability,
                it.lastReviewEpoch, it.dueEpoch, it.status.name)
        }
        val logs = db.logs().recent(1500).map {
            LogDTO(it.wordId, it.axis.name, it.epoch, it.quality, it.response, it.errorTags, it.provisional)
        }
        val backup = Backup(
            exportedAt = System.currentTimeMillis(),
            prefs = PrefsDTO(
                examDate = p[Keys.EXAM_DATE] ?: "2027-02-15",
                streak = p[Keys.STREAK] ?: 0, longest = p[Keys.LONGEST] ?: 0, lastStudy = p[Keys.LAST_STUDY],
                freezes = p[Keys.FREEZES] ?: 2, freezeMonth = p[Keys.FREEZE_MONTH],
                newToday = p[Keys.NEW_TODAY] ?: 0, newTodayDate = p[Keys.NEW_TODAY_DATE],
                academic = p[Keys.ACADEMIC] ?: false, capOverride = p[Keys.CAP_OVERRIDE],
                calibrated = p[Keys.CALIBRATED] ?: false,
                emaP = p[Keys.EMA_P] ?: 0.0, emaC = p[Keys.EMA_C] ?: 0.0, emaR = p[Keys.EMA_R] ?: 0.0,
                history = p[Keys.HISTORY] ?: "",
                themeMode = p[Keys.THEME_MODE] ?: "DARK",
                tier6 = p[Keys.TIER6] ?: false, tier7 = p[Keys.TIER7] ?: false, tier8 = p[Keys.TIER8] ?: false,
                writingStrength = p[Keys.WRITING_STRENGTH] ?: 0.0,
                xp = p[Keys.XP] ?: 0L,
                essays = p[Keys.ESSAYS] ?: 0,
                bestWriting = p[Keys.BEST_WRITING] ?: 0.0,
                questsDone = p[Keys.QUESTS_DONE] ?: 0,
            ),
            axes = axes, logs = logs,
        )
        return json.encodeToString(backup)
    }

    suspend fun importJson(text: String) {
        val backup = json.decodeFromString<Backup>(text)
        db.axes().clearAll(); db.logs().clearAll()
        db.axes().upsertAll(backup.axes.map {
            AxisState(it.wordId, Axis.valueOf(it.axis), it.intervalDays, it.ease, it.reps, it.stability,
                it.lastReviewEpoch, it.dueEpoch, AxisStatus.valueOf(it.status))
        })
        backup.logs.forEach {
            db.logs().insert(ReviewLog(0, it.wordId, Axis.valueOf(it.axis), it.epoch, it.quality,
                it.response, it.errorTags, it.provisional))
        }
        val pr = backup.prefs
        ctx.dataStore.edit { p ->
            p[Keys.EXAM_DATE] = pr.examDate
            p[Keys.STREAK] = pr.streak; p[Keys.LONGEST] = pr.longest
            pr.lastStudy?.let { p[Keys.LAST_STUDY] = it }
            p[Keys.FREEZES] = pr.freezes; pr.freezeMonth?.let { p[Keys.FREEZE_MONTH] = it }
            p[Keys.NEW_TODAY] = pr.newToday; pr.newTodayDate?.let { p[Keys.NEW_TODAY_DATE] = it }
            p[Keys.ACADEMIC] = pr.academic; pr.capOverride?.let { p[Keys.CAP_OVERRIDE] = it }
            p[Keys.CALIBRATED] = pr.calibrated
            p[Keys.EMA_P] = pr.emaP; p[Keys.EMA_C] = pr.emaC; p[Keys.EMA_R] = pr.emaR
            p[Keys.HISTORY] = pr.history
            p[Keys.THEME_MODE] = pr.themeMode
            p[Keys.TIER6] = pr.tier6; p[Keys.TIER7] = pr.tier7; p[Keys.TIER8] = pr.tier8
            p[Keys.WRITING_STRENGTH] = pr.writingStrength
            p[Keys.XP] = pr.xp
            p[Keys.ESSAYS] = pr.essays
            p[Keys.BEST_WRITING] = pr.bestWriting
            p[Keys.QUESTS_DONE] = pr.questsDone
        }
    }

    companion object {
        @Volatile private var instance: VocabRepository? = null
        fun get(ctx: Context): VocabRepository =
            instance ?: synchronized(this) {
                instance ?: VocabRepository(ctx.applicationContext).also { instance = it }
            }
    }
}
