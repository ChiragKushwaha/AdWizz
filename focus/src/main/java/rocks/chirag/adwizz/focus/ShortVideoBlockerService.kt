package rocks.chirag.adwizz.focus

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Backs out of Reels / Shorts in apps and browsers (and closes short-video-only apps) while the
 * "Block Reels & Shorts" switch in AdWizz is on. In apps it only checks which screen is open; in browsers
 * it reads the address bar, nothing else on the page.
 */
class ShortVideoBlockerService : AccessibilityService() {
    private var switch: FocusSwitch? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingPkg: String? = null
    private var lastActionAt = 0L
    private var lastToastAt = 0L

    private val check = Runnable { pendingPkg?.let { pendingPkg = null; checkScreen(it) } }

    override fun onServiceConnected() {
        switch = FocusSwitch(this).also { it.start() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (switch?.on != true) return
        val pkg = event.packageName?.toString() ?: return
        val sinceAction = SystemClock.uptimeMillis() - lastActionAt

        if (pkg in ShortVideoRules.WHOLE_APPS) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && sinceAction >= COOLDOWN_MS) {
                leave(GLOBAL_ACTION_HOME)
            }
            return
        }
        if (pkg !in ShortVideoRules.SCREEN_IDS && pkg !in ShortVideoRules.BROWSER_URL_IDS) return
        // Events arrive in bursts: check once things settle. Right after pressing Back, wait out
        // the cooldown and look again, since Back can land on another short (e.g. YouTube's site).
        pendingPkg = pkg
        handler.removeCallbacks(check)
        handler.postDelayed(check, maxOf(SETTLE_MS, COOLDOWN_MS - sinceAction))
    }

    private fun checkScreen(pkg: String) {
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != pkg) return
            if (onShortVideo(root, pkg)) leave(GLOBAL_ACTION_BACK)
        } catch (_: RuntimeException) {
            // The window can vanish mid-read; the next event will try again.
        }
    }

    private fun onShortVideo(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val screenIds = ShortVideoRules.viewIdsFor(pkg)
        if (screenIds.isNotEmpty()) {
            return screenIds.any { id -> root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser } }
        }
        // Browser: skip while the user is typing in the address bar.
        return ShortVideoRules.urlBarIdsFor(pkg).any { id ->
            root.findAccessibilityNodeInfosByViewId(id).any { bar ->
                !bar.isFocused && ShortVideoRules.isShortVideoUrl(bar.text?.toString().orEmpty())
            }
        }
    }

    private fun leave(action: Int) {
        val now = SystemClock.uptimeMillis()
        lastActionAt = now
        performGlobalAction(action)
        if (now - lastToastAt > TOAST_GAP_MS) {
            lastToastAt = now
            Toast.makeText(this, "Short videos are blocked by AdWizz", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(check)
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        switch?.stop()
        super.onDestroy()
    }

    companion object {
        private const val SETTLE_MS = 200L
        /** Gives the app time to leave the screen before we look again, so we don't double-back. */
        private const val COOLDOWN_MS = 1000L
        private const val TOAST_GAP_MS = 4000L
    }
}
