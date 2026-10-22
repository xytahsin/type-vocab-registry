package com.tahsin.vocabregistry.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.FullScreenContentCallback

/**
 * All advertising in one place. Currently wired to Google's OFFICIAL TEST unit IDs,
 * which serve real-looking test ads with zero policy risk. Before the Play release,
 * replace the three IDs below (and the APPLICATION_ID in AndroidManifest.xml) with
 * real ones from the AdMob console.
 *
 * Every entry point is a no-op when the user is Pro, and every call is wrapped so an
 * ads failure can never crash the app.
 */
object Ads {
    // ---- TEST IDs (Google's official demo units) ----
    const val BANNER_UNIT = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_UNIT = "ca-app-pub-3940256099942544/1033173712"

    /** Show an interstitial at most every Nth completed session… */
    private const val SESSION_INTERVAL = 3
    /** …and never more often than this many minutes apart. */
    private const val MIN_MINUTES_BETWEEN = 4L

    private var interstitial: InterstitialAd? = null
    private var sessionsSinceAd = 0
    private var lastShownAt = 0L
    @Volatile private var initialized = false

    fun init(ctx: Context) {
        if (initialized) return
        runCatching {
            MobileAds.initialize(ctx) {}
            initialized = true
            preloadInterstitial(ctx)
        }
    }

    private fun preloadInterstitial(ctx: Context) {
        runCatching {
            InterstitialAd.load(
                ctx, INTERSTITIAL_UNIT, AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                    override fun onAdFailedToLoad(err: LoadAdError) { interstitial = null }
                },
            )
        }
    }

    /**
     * Call when a study session finishes. Honors Pro, the session interval and the
     * time gap; quietly does nothing if no ad is ready.
     */
    fun onSessionFinished(activity: Activity, isPro: Boolean) {
        if (isPro) return
        sessionsSinceAd++
        val now = System.currentTimeMillis()
        val dueByCount = sessionsSinceAd >= SESSION_INTERVAL
        val dueByTime = now - lastShownAt >= MIN_MINUTES_BETWEEN * 60_000L
        val ad = interstitial
        if (dueByCount && dueByTime && ad != null) {
            runCatching {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitial = null
                        preloadInterstitial(activity.applicationContext)
                    }
                }
                ad.show(activity)
                sessionsSinceAd = 0
                lastShownAt = now
            }
        } else if (ad == null && initialized) {
            preloadInterstitial(activity.applicationContext)
        }
    }
}

/** A standard banner. Renders nothing for Pro users. */
@Composable
fun AdBanner(isPro: Boolean, modifier: Modifier = Modifier) {
    if (isPro) return
    val ctx = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = Ads.BANNER_UNIT
                runCatching { loadAd(AdRequest.Builder().build()) }
            }
        },
    )
}
