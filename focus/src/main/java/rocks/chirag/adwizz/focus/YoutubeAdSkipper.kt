package rocks.chirag.adwizz.focus

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Looks at YouTube's player: taps "Skip" as soon as it shows, and reports whether an ad is still
 * playing so it can be muted. Reads only the player's own view IDs, nothing you watch or type.
 */
internal object YoutubeAdSkipper {
    enum class Result { NO_AD, AD_PLAYING, SKIPPED }

    fun check(root: AccessibilityNodeInfo, pkg: String): Result {
        val skip = AdRules.idsFor(pkg, AdRules.SKIP_BUTTON_IDS).firstNotNullOfOrNull { id ->
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isVisibleToUser }
        }
        if (skip != null && click(skip)) return Result.SKIPPED
        val adPlaying = AdRules.idsFor(pkg, AdRules.AD_PLAYING_IDS).any { id ->
            root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }
        }
        if (!adPlaying) return Result.NO_AD
        // The button's ID changed but an ad is on: look for its label instead.
        val byText = AdRules.SKIP_WORDS.firstNotNullOfOrNull { word ->
            root.findAccessibilityNodeInfosByText(word).firstOrNull { node ->
                node.isVisibleToUser && labelOf(node).startsWith(word, ignoreCase = true)
            }
        }
        return if (byText != null && click(byText)) Result.SKIPPED else Result.AD_PLAYING
    }

    private fun labelOf(node: AccessibilityNodeInfo): String =
        (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()

    /** Clicks the node, or the nearest clickable parent (the label is often inside the button). */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 4) {
            if (n.isClickable && n.isEnabled) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
            depth++
        }
        return false
    }
}
