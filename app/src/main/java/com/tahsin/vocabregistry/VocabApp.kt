package com.tahsin.vocabregistry

import android.app.Application
import androidx.work.*
import com.tahsin.vocabregistry.notify.ReviewWorker
import java.util.concurrent.TimeUnit

class VocabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val req = PeriodicWorkRequestBuilder<ReviewWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "review-reminder", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
