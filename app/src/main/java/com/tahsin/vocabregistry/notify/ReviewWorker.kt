package com.tahsin.vocabregistry.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tahsin.vocabregistry.data.VocabRepository

/** Applies decay, then nudges with the live due count. Quiet when nothing is due. */
class ReviewWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = VocabRepository.get(applicationContext)
        repo.dailyHousekeeping()
        val due = repo.db.axes().due(System.currentTimeMillis()).size
        if (due > 0) notify(due)
        return Result.success()
    }
    private fun notify(due: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("reviews", "Review reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(applicationContext, "reviews")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("$due items due in the Registry")
            .setContentText("Production fades fastest — a 12-card Sprint clears the backlog.")
            .setAutoCancel(true).build()
        runCatching { nm.notify(1, n) }
    }
}
