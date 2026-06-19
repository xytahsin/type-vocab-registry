package com.tahsin.vocabregistry.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tahsin.vocabregistry.data.Keys
import com.tahsin.vocabregistry.data.VocabRepository
import com.tahsin.vocabregistry.data.model.Word
import java.time.LocalDate

/** Daily housekeeping + two nudges: a review reminder and one Word of the Day per day. */
class ReviewWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = VocabRepository.get(applicationContext)
        repo.dailyHousekeeping()

        val due = repo.db.axes().due(System.currentTimeMillis()).size
        if (due > 0) notifyReview(due)

        val today = LocalDate.now().toString()
        val p = repo.prefs()
        if (p[Keys.WOTD_DATE] != today) {
            repo.wordOfDay()?.let { notifyWord(it) }
            repo.edit { it[Keys.WOTD_DATE] = today }
        }
        return Result.success()
    }

    private fun channel(id: String, name: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun manager() =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun notifyReview(due: Int) {
        channel("reviews", "Review reminders")
        val n = NotificationCompat.Builder(applicationContext, "reviews")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("$due items due in Tahsincabs")
            .setContentText("Production fades fastest \u2014 a 12-card Sprint clears the backlog.")
            .setAutoCancel(true).build()
        runCatching { manager().notify(1, n) }
    }

    private fun notifyWord(w: Word) {
        channel("word_of_day", "Word of the day")
        val n = NotificationCompat.Builder(applicationContext, "word_of_day")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Word of the day: ${w.word}")
            .setContentText("${w.pos} \u2014 ${w.definition}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${w.word}  (${w.pos})\n${w.definition}\n\n\u201C${w.example}\u201D"))
            .setAutoCancel(true).build()
        runCatching { manager().notify(2, n) }
    }
}
