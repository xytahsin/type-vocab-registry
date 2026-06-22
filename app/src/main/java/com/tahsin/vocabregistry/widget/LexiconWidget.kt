package com.tahsin.vocabregistry.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tahsin.vocabregistry.MainActivity
import com.tahsin.vocabregistry.R
import com.tahsin.vocabregistry.data.Keys
import com.tahsin.vocabregistry.data.VocabRepository
import kotlinx.coroutines.runBlocking

/** A small home-screen widget: today's due count, streak, readiness band, word of the day. */
class LexiconWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val prefs = runBlocking { VocabRepository.get(ctx).prefs() }
        val due = prefs[Keys.WIDGET_DUE] ?: 0
        val streak = prefs[Keys.WIDGET_STREAK] ?: 0
        val band = prefs[Keys.WIDGET_BAND] ?: "\u2014"
        val wotd = prefs[Keys.WIDGET_WOTD] ?: "\u2014"

        for (id in ids) {
            val views = RemoteViews(ctx.packageName, R.layout.lexicon_widget).apply {
                setTextViewText(R.id.w_due, if (due == 1) "1 card due" else "$due cards due")
                setTextViewText(R.id.w_band, "Band $band")
                setTextViewText(R.id.w_streak, "$streak\u2011day streak")
                setTextViewText(R.id.w_wotd, "Word: $wotd")
                setOnClickPendingIntent(R.id.w_root, openAppIntent(ctx))
            }
            mgr.updateAppWidget(id, views)
        }
    }

    private fun openAppIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** Persist the latest values and ask the launcher to redraw any placed widgets. */
        suspend fun push(ctx: Context, due: Int, streak: Int, band: String, wotd: String) {
            val repo = VocabRepository.get(ctx)
            repo.edit {
                it[Keys.WIDGET_DUE] = due
                it[Keys.WIDGET_STREAK] = streak
                it[Keys.WIDGET_BAND] = band
                it[Keys.WIDGET_WOTD] = wotd
            }
            updateAll(ctx)
        }

        /** Recompute values from the repository, then push (used by the background worker). */
        suspend fun refreshFromRepo(ctx: Context) {
            val repo = VocabRepository.get(ctx)
            val due = repo.db.axes().due(System.currentTimeMillis()).size
            val p = repo.prefs()
            val streak = p[Keys.STREAK] ?: 0
            val bandVal = p[Keys.LAST_BAND_SEEN] ?: 0.0
            val band = if (bandVal > 0.0) "%.1f".format(bandVal) else "\u2014"
            val wotd = repo.wordOfDay()?.word ?: "\u2014"
            push(ctx, due, streak, band, wotd)
        }

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, LexiconWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(ctx, LexiconWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                ctx.sendBroadcast(intent)
            }
        }
    }
}
