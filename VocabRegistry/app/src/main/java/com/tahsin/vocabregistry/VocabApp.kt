package com.tahsin.vocabregistry

import android.app.Application
import com.tahsin.vocabregistry.ads.Ads
import com.tahsin.vocabregistry.notify.Reminders

class VocabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Schedule (or cancel) the daily study reminder according to saved preferences.
        Reminders.sync(this)
        // Mobile ads (no-op on failure; banners/interstitials honor Pro at call sites).
        Ads.init(this)
    }
}
