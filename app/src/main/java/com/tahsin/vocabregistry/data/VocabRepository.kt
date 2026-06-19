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
}

class VocabRepository private constructor(private val ctx: Context) {
    val db: AppDatabase = Room.databaseBuilder(ctx, AppDatabase::class.java, "vocab.db").build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded() {
        if (db.words().count() > 0) return
        val raw = ctx.assets.open("vocab.json").bufferedReader().use { it.readText() }
        val items = json.decodeFromString<List<WordJson>>(raw)
        db.words().insertAll(items.map {
            Word(it.i, it.w, it.p, it.t, it.h, it.d, it.e,
                it.c.joinToString("|"), it.s.joinToString(";"), it.x)
        })
    }

    suspend fun axesMap(): Map<Int, Map<Axis, AxisState>> =
        db.axes().all().groupBy { it.wordId }.mapValues { (_, v) -> v.associateBy { it.axis } }

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

    companion object {
        @Volatile private var instance: VocabRepository? = null
        fun get(ctx: Context): VocabRepository =
            instance ?: synchronized(this) {
                instance ?: VocabRepository(ctx.applicationContext).also { instance = it }
            }
    }
}
