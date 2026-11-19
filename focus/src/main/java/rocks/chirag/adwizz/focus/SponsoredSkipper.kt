package rocks.chirag.adwizz.focus

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Scrolls sponsored cards in YouTube and Instagram feeds out of view, so they're gone instead of
 * covered. Apps can't change each other's views, but an Accessibility service may scroll them:
 *  - a feed card: once the feed stops moving, swipe exactly far enough to push it above the list;
 *  - a full-screen sponsored reel/short: go to the next one.
 * If the user scrolls back to a card we just skipped, they meant to see it: leave it alone.
 */
internal class SponsoredSkipper(private val service: AccessibilityService) {
    private var lastKey: String? = null
    private var lastSkipAt = 0L
    private var busyUntil = 0L

    /** Finds a sponsored card on screen and skips it. Returns true if it acted. */
    fun update(root: AccessibilityNodeInfo): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now < busyUntil) return false   // our own swipe is still running
        val screen = Rect().also { root.getBoundsInScreen(it) }
        val label = find(root, 0) ?: return false
        val (card, list) = cardAndList(label) ?: return false
        val cardRect = Rect().also { card.getBoundsInScreen(it) }
        val listRect = Rect().also { list.getBoundsInScreen(it) }
        val key = "${label.text ?: label.contentDescription}|${cardRect.height()}"
        if (key == lastKey && now - lastSkipAt < REVISIT_MS) return false
        lastKey = key; lastSkipAt = now

        val fullScreen = cardRect.height() >= screen.height() * 8 / 10
        val acted = if (fullScreen) list.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id)
        else swipeUp(listRect, distance = cardRect.bottom - listRect.top)
        if (BuildConfig.DEBUG) Log.d(TAG, "skip sponsored card=$cardRect list=$listRect fullScreen=$fullScreen acted=$acted")
        return acted
    }

    private fun find(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > MAX_TREE_DEPTH) return null
        if (n.isVisibleToUser && (AdRules.isSponsoredLabel(n.contentDescription?.toString()) || AdRules.isSponsoredLabel(n.text?.toString()))) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> find(c, depth + 1)?.let { return it } }
        return null
    }

    /** The list item holding the label, and the scrolling list it sits in. */
    private fun cardAndList(label: AccessibilityNodeInfo): Pair<AccessibilityNodeInfo, AccessibilityNodeInfo>? {
        var node = label
        repeat(MAX_DEPTH) {
            val parent = node.parent ?: return null
            if (parent.isScrollable) return node to parent
            node = parent
        }
        return null
    }

    /**
     * Drags the list up by [distance] px, then holds still briefly so it doesn't fling past
     * the real posts that follow.
     */
    private fun swipeUp(list: Rect, distance: Int): Boolean {
        if (distance <= 0) return false
        val travel = minOf(distance + SLOP_PX, list.height() * 85 / 100)
        val x = list.centerX().toFloat()
        val startY = (list.bottom - list.height() / 20).toFloat()
        val endY = startY - travel
        val move = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        val dur = (travel / 2L).coerceIn(250L, 700L)
        if (android.os.Build.VERSION.SDK_INT < 26) {
            // Android 7.x can't continue a stroke into a hold: a plain swipe (may fling a little further).
            busyUntil = SystemClock.uptimeMillis() + dur + 150
            val swipe = GestureDescription.StrokeDescription(move, 0, dur)
            return service.dispatchGesture(GestureDescription.Builder().addStroke(swipe).build(), null, null)
        }
        val stroke = GestureDescription.StrokeDescription(move, 0, dur, true)
        val hold = stroke.continueStroke(Path().apply { moveTo(x, endY); lineTo(x, endY + 0.5f) }, 0, HOLD_MS, false)
        busyUntil = SystemClock.uptimeMillis() + dur + HOLD_MS + 150
        val first = service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                service.dispatchGesture(GestureDescription.Builder().addStroke(hold).build(), null, null)
            }
        }, null)
        return first
    }

    private companion object {
        const val TAG = "AdWizzAds"
        const val MAX_DEPTH = 12
        const val MAX_TREE_DEPTH = 40
        /** Scrolled back to the same card this soon: the user wants to see it. */
        const val REVISIT_MS = 6000L
        const val HOLD_MS = 120L
        /** A little extra, so the card's edge doesn't peek in at the top. */
        const val SLOP_PX = 8
    }
}
