package com.tahsin.vocabregistry.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tahsin.vocabregistry.data.Keys
import com.tahsin.vocabregistry.data.VocabRepository
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Schedules (or cancels) the once-a-day study reminder at the user's chosen hour. */
object Reminders {
    private const val WORK = "review-reminder"

    fun sync(ctx: Context) {
        val repo = VocabRepository.get(ctx)
        val prefs = runBlocking { repo.prefs() }
        val on = prefs[Keys.REMINDER_ON] ?: true
        val hour = (prefs[Keys.REMINDER_HOUR] ?: 19).coerceIn(0, 23)
        val wm = WorkManager.getInstance(ctx)
        if (!on) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val req = PeriodicWorkRequestBuilder<ReviewWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilHour(hour), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build(),
            )
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun millisUntilHour(hour: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
