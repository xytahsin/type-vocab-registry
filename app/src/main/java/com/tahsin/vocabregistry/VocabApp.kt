package com.tahsin.vocabregistry

import android.app.Application
import com.tahsin.vocabregistry.notify.Reminders

class VocabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Schedule (or cancel) the daily study reminder according to saved preferences.
        Reminders.sync(this)
    }
}
