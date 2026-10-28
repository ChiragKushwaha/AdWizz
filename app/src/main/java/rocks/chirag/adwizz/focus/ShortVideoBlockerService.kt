package rocks.chirag.adwizz.focus

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import rocks.chirag.adwizz.data.Settings

/**
 * Backs out of Instagram Reels / YouTube Shorts (and closes short-video-only apps) while the
 * "Block Reels & Shorts" switch is on. It only reads which screen is open; it never reads text.
 */
class ShortVideoBlockerService : AccessibilityService() {
    private lateinit var settings: Settings
    private val handler = Handler(Looper.getMainLooper())
    private var pendingPkg: String? = null
    private var lastActionAt = 0L

    private val check = Runnable { pendingPkg?.let { pendingPkg = null; checkScreen(it) } }

    override fun onServiceConnected() {
        settings = Settings(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::settings.isInitialized || !settings.blockShortVideos) return
        val pkg = event.packageName?.toString() ?: return
        if (SystemClock.uptimeMillis() - lastActionAt < COOLDOWN_MS) return

        if (pkg in ShortVideoRules.WHOLE_APPS) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) leave(GLOBAL_ACTION_HOME)
            return
        }
        if (pkg !in ShortVideoRules.SCREEN_IDS) return
        // Content-change events arrive in bursts: check once things settle, not on every event.
        pendingPkg = pkg
        handler.removeCallbacks(check)
        handler.postDelayed(check, SETTLE_MS)
    }

    private fun checkScreen(pkg: String) {
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != pkg) return
            val onShortVideo = ShortVideoRules.viewIdsFor(pkg).any { id ->
                root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }
            }
            if (onShortVideo) leave(GLOBAL_ACTION_BACK)
        } catch (_: RuntimeException) {
            // The window can vanish mid-read; the next event will try again.
        }
    }

    private fun leave(action: Int) {
        lastActionAt = SystemClock.uptimeMillis()
        performGlobalAction(action)
        Toast.makeText(this, "Short videos are blocked by AdWizz", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {
        handler.removeCallbacks(check)
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        super.onDestroy()
    }

    companion object {
        private const val SETTLE_MS = 200L
        /** Gives the app time to leave the screen before we look again, so we don't double-back. */
        private const val COOLDOWN_MS = 1200L

        /** True if the user has turned this service on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, ShortVideoBlockerService::class.java)
            val enabled = AndroidSettings.Secure.getString(
                context.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
