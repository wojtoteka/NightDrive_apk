package wojtoteka.nightdrive.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import wojtoteka.nightdrive.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob wrapper: GDPR consent (UMP), an interstitial shown when leaving the game-over screen or
 * quitting to menu from pause (frequency-capped), and a rewarded ad that doubles the coins from
 * the finished run.
 *
 * Everything fails soft: if consent is missing, the network is down or nothing is loaded, the
 * game continues instantly and the rewarded button simply doesn't appear. Debug builds use
 * Google's public sample ad units (always fill, safe to click); release uses the real units.
 */
class AdsController(private val activity: Activity) {

    /** True when a rewarded ad is loaded — gates the "double coins" button on game over. */
    var rewardedReady by mutableStateOf(false); private set

    /** True when GDPR rules require offering a "change your consent" entry point in settings. */
    var privacyOptionsRequired by mutableStateOf(false); private set

    private val main = Handler(Looper.getMainLooper())
    private val sdkStarted = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var lastInterstitialAt = 0L

    fun start() {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                privacyOptionsRequired = consent.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consent.canRequestAds()) initSdk()
                }
            },
            {
                // Offline / misconfigured message — fall back to any consent cached earlier.
                if (consent.canRequestAds()) initSdk()
            },
        )
        // Consent already cached from a previous session: start immediately, the update above
        // only refreshes it in the background.
        if (consent.canRequestAds()) initSdk()
    }

    /** Re-opens the UMP consent form from Settings (required by Google when GDPR applies). */
    fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    /**
     * Called when the player leaves the game-over screen (play again / menu / back) or quits to
     * the menu from pause. Shown every time, capped only by [MIN_INTERVAL_MS] so rapid
     * back-to-back exits don't stack ads on top of each other. [onDone] always runs — immediately
     * if no ad is shown, otherwise when the ad is dismissed.
     */
    fun maybeShowInterstitial(onDone: () -> Unit) {
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        val tooSoon = lastInterstitialAt != 0L && now - lastInterstitialAt < MIN_INTERVAL_MS
        if (ad == null || tooSoon) { onDone(); return }
        interstitial = null
        lastInterstitialAt = now
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { loadInterstitial(); onDone() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { loadInterstitial(); onDone() }
        }
        ad.show(activity)
    }

    /** Shows the rewarded ad; [onReward] fires only when the ad was actually watched. */
    fun showRewarded(onReward: () -> Unit) {
        val ad = rewarded ?: return
        rewarded = null
        rewardedReady = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { loadRewarded() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { loadRewarded() }
        }
        ad.show(activity) { onReward() }
    }

    /** Cancels pending retry timers so they don't hold [activity] alive after it's destroyed. */
    fun destroy() {
        main.removeCallbacksAndMessages(null)
    }

    // ---- Internals -------------------------------------------------------------------------

    private fun initSdk() {
        if (!sdkStarted.compareAndSet(false, true)) return
        // Init off the main thread (it does disk/network work); ad loads back on main.
        Thread {
            MobileAds.initialize(activity) {
                main.post { loadInterstitial(); loadRewarded() }
            }
        }.start()
    }

    private fun loadInterstitial() {
        if (INTERSTITIAL_UNIT.isEmpty()) return
        InterstitialAd.load(
            activity, INTERSTITIAL_UNIT, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    main.postDelayed({ loadInterstitial() }, RETRY_MS)
                }
            },
        )
    }

    private fun loadRewarded() {
        if (REWARDED_UNIT.isEmpty()) return
        RewardedAd.load(
            activity, REWARDED_UNIT, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; rewardedReady = true }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                    rewardedReady = false
                    main.postDelayed({ loadRewarded() }, RETRY_MS)
                }
            },
        )
    }

    private companion object {
        // Debug builds show an interstitial on every call, no cooldown, for easy testing.
        // Release keeps a 30s floor between interstitials — showing one on literally every
        // exit with no minimum gap risks tripping AdMob's policy on disruptive ad frequency.
        val MIN_INTERVAL_MS = if (BuildConfig.DEBUG) 0L else 30_000L
        const val RETRY_MS = 45_000L

        // Google's public test units — always fill, allowed to click. Used by debug builds.
        const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

        const val PROD_INTERSTITIAL = "ca-app-pub-6447964689260393/8344256380"
        const val PROD_REWARDED = "ca-app-pub-6447964689260393/3403609250"

        val INTERSTITIAL_UNIT = if (BuildConfig.DEBUG) TEST_INTERSTITIAL else PROD_INTERSTITIAL
        val REWARDED_UNIT = if (BuildConfig.DEBUG) TEST_REWARDED else PROD_REWARDED
    }
}
