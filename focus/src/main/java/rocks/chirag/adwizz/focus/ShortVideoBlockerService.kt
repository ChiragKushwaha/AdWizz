package rocks.chirag.adwizz.focus

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * The add-on's Accessibility service, following the switches in AdWizz:
 *  - Block Reels & Shorts: backs out of short-video screens and closes short-video-only apps.
 *  - Skip YouTube ads: taps "Skip" as soon as it shows and mutes ads that can't be skipped.
 *  - Mute Spotify ads: mutes while Spotify plays an ad (from Spotify's own playback broadcasts).
 * Paused from AdWizz, it stops receiving events at all. If AdWizz is uninstalled, it asks to be
 * uninstalled too and switches itself off.
 */
class ShortVideoBlockerService : AccessibilityService() {
    private lateinit var switch: FocusSwitch
    private lateinit var muter: AdMuter
    private lateinit var receivers: AddonReceivers
    private lateinit var sponsored: SponsoredHider
    private val handler = Handler(Looper.getMainLooper())
    private val reels = ReelsBlocker(::leave)
    private var pendingPkg: String? = null
    private var lastActionAt = 0L
    private var lastToastAt = 0L
    private var watchedEvents = 0
    private var listening = true

    private val check = Runnable { pendingPkg?.let { pendingPkg = null; checkScreen(it) } }
    private val resume = Runnable { apply() }
    /** While a YouTube ad is on, look again regularly: "Skip" appears without a screen change. */
    private val adPoll = Runnable { pollYoutubeAd() }
    /** Sponsored panels follow the feed: re-checked quickly on scrolls, and while shown. */
    private val sponsoredCheck = Runnable { checkSponsored() }

    override fun onServiceConnected() {
        watchedEvents = serviceInfo?.eventTypes ?: 0
        muter = AdMuter(getSystemService(AudioManager::class.java))
        sponsored = SponsoredHider(this)
        val spotify = SpotifyAdDetector(
            // postAtTime with a token works on every Android version (postDelayed with one needs 9+).
            schedule = { delay, run -> handler.postAtTime(run, SPOTIFY_TOKEN, SystemClock.uptimeMillis() + delay) },
            cancel = { handler.removeCallbacksAndMessages(SPOTIFY_TOKEN) },
            onAd = { ad -> muter.set(AdMuter.Source.SPOTIFY, ad) },
        )
        receivers = AddonReceivers(this, spotify, ::onMainAppRemoved).also { it.start() }
        switch = FocusSwitch(this, ::apply).also { it.start() }
        if (!isMainAppInstalled()) { onMainAppRemoved(); return }
        apply()
    }

    /** Re-reads the switches: starts or stops watching, handles pauses and "turn off". */
    private fun apply() {
        handler.removeCallbacks(resume)
        if (switch.offRequest > handledOffRequest()) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_OFF, switch.offRequest).apply()
            shutDown(); disableSelf(); return
        }
        val active = switch.active()
        if (BuildConfig.DEBUG) Log.d(TAG, "apply: active=$active reels=${switch.on} youtube=${switch.skipYoutubeAds} spotify=${switch.muteSpotifyAds} sponsored=${switch.hideSponsored} reachable=${switch.reachable}")
        setListening(active)
        receivers.listenToSpotify(active && switch.muteSpotifyAds)
        if (!active || !switch.skipYoutubeAds) { handler.removeCallbacks(adPoll); muter.set(AdMuter.Source.YOUTUBE, false) }
        if (!active || !switch.hideSponsored) { handler.removeCallbacks(sponsoredCheck); sponsored.clear() }
        // A timed pause ends on its own.
        val until = switch.pausedUntil
        if (switch.isPaused() && until != Long.MAX_VALUE) handler.postDelayed(resume, until - System.currentTimeMillis() + 500)
    }

    /** Paused or with nothing switched on, ask Android for no events at all. */
    private fun setListening(on: Boolean) {
        if (on == listening) return
        listening = on
        val info = serviceInfo ?: return
        info.eventTypes = if (on) watchedEvents else 0
        runCatching { serviceInfo = info }.onFailure { Log.w(TAG, "Couldn't change events", it) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::switch.isInitialized || !switch.active()) return
        val pkg = event.packageName?.toString() ?: return
        val sinceAction = SystemClock.uptimeMillis() - lastActionAt
        if (switch.on && pkg in ShortVideoRules.WHOLE_APPS) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && sinceAction >= COOLDOWN_MS) {
                leave(GLOBAL_ACTION_HOME)
            }
            return
        }
        if (switch.hideSponsored && pkg in AdRules.SPONSORED_APPS) {
            handler.removeCallbacks(sponsoredCheck)
            handler.postDelayed(sponsoredCheck, SPONSORED_MS)
        }
        val forReels = switch.on && (pkg in ShortVideoRules.SCREEN_IDS || pkg in ShortVideoRules.BROWSER_URL_IDS)
        val forAds = switch.skipYoutubeAds && pkg in AdRules.YOUTUBE_PACKAGES
        if (!forReels && !forAds) return
        // Events arrive in bursts: check once things settle. Right after pressing Back, wait out
        // the cooldown and look again, since Back can land on another short (e.g. YouTube's site).
        pendingPkg = pkg
        handler.removeCallbacks(check)
        handler.postDelayed(check, if (forReels) maxOf(SETTLE_MS, COOLDOWN_MS - sinceAction) else SETTLE_MS)
    }

    private fun checkScreen(pkg: String) {
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != pkg) return
            if (switch.skipYoutubeAds && pkg in AdRules.YOUTUBE_PACKAGES) checkYoutubeAd(root, pkg)
            if (switch.on) reels.check(root, pkg)
        } catch (_: RuntimeException) {
            // The window can vanish mid-read; the next event will try again.
        }
    }

    private fun checkYoutubeAd(root: AccessibilityNodeInfo, pkg: String) {
        val result = YoutubeAdSkipper.check(root, pkg)
        if (BuildConfig.DEBUG) DebugIds.log(root, result.name)
        val adOn = result == YoutubeAdSkipper.Result.AD_PLAYING
        muter.set(AdMuter.Source.YOUTUBE, adOn)
        handler.removeCallbacks(adPoll)
        if (result != YoutubeAdSkipper.Result.NO_AD) handler.postDelayed(adPoll, AD_POLL_MS)
    }

    private fun pollYoutubeAd() {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        if (root == null || pkg !in AdRules.YOUTUBE_PACKAGES || !switch.active() || !switch.skipYoutubeAds) {
            muter.set(AdMuter.Source.YOUTUBE, false)   // left YouTube: never leave the phone muted
            return
        }
        runCatching { checkYoutubeAd(root, pkg!!) }
    }

    private fun checkSponsored() {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        if (root == null || pkg !in AdRules.SPONSORED_APPS || !switch.active() || !switch.hideSponsored) {
            sponsored.clear()   // left the app: never leave panels over another screen
            return
        }
        runCatching { sponsored.update(root) }.onFailure { Log.w(TAG, "Sponsored check failed", it) }
        // Keep following while panels are up (scrolls without events, or leaving the app).
        if (sponsored.showing) handler.postDelayed(sponsoredCheck, SPONSORED_POLL_MS)
    }

    private fun leave(action: Int) {
        val now = SystemClock.uptimeMillis()
        lastActionAt = now
        performGlobalAction(action)
        if (now - lastToastAt > TOAST_GAP_MS) {
            lastToastAt = now
            Toast.makeText(this, getString(R.string.short_videos_blocked), Toast.LENGTH_SHORT).show()
        }
    }

    private fun isMainAppInstalled() = runCatching {
        packageManager.getPackageInfo(BuildConfig.MAIN_APP_ID, 0); true
    }.getOrElse { it !is PackageManager.NameNotFoundException }

    /** AdWizz is gone: there's nothing to follow, so ask to remove the add-on and switch off. */
    private fun onMainAppRemoved() {
        shutDown()
        // Accessibility services may open screens from the background.
        runCatching { startActivity(AddonReceivers.uninstallSelf(this)) }.onFailure { Log.w(TAG, "Couldn't ask to uninstall", it) }
        disableSelf()
    }

    private fun handledOffRequest() = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_OFF, 0L)

    private fun shutDown() {
        handler.removeCallbacksAndMessages(null)
        if (::receivers.isInitialized) receivers.stop()
        if (::muter.isInitialized) muter.releaseAll()
        if (::sponsored.isInitialized) sponsored.clear()
    }

    override fun onInterrupt() {
        handler.removeCallbacks(check)
    }

    override fun onDestroy() {
        shutDown()
        if (::switch.isInitialized) switch.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdWizzFocus"
        private const val SETTLE_MS = 200L
        /** Gives the app time to leave the screen before we look again, so we don't double-back. */
        private const val COOLDOWN_MS = 1000L
        private const val TOAST_GAP_MS = 4000L
        private const val AD_POLL_MS = 700L
        private const val SPONSORED_MS = 30L
        private const val SPONSORED_POLL_MS = 400L
        private const val PREFS = "addon"
        private const val KEY_OFF = "off_handled"
        private val SPOTIFY_TOKEN = Any()
    }
}
