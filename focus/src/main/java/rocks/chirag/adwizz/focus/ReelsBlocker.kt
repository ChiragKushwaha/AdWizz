package rocks.chirag.adwizz.focus

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/**
 * "Block Reels & Shorts": presses Back on a short-video screen. In apps it only checks which screen
 * is open; in browsers it reads the address bar, nothing else on the page.
 */
internal class ReelsBlocker(private val leave: (Int) -> Unit) {
    fun check(root: AccessibilityNodeInfo, pkg: String) {
        if (onShortVideo(root, pkg)) leave(AccessibilityService.GLOBAL_ACTION_BACK)
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
}
