package rocks.chirag.adwizz.focus

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Covers sponsored cards in YouTube and Instagram feeds with a plain panel. Apps can't remove each
 * other's views, but an Accessibility service may draw on top of them. Touches pass through, so
 * the feed scrolls normally; the panels follow the cards as they move.
 */
internal class SponsoredHider(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val panels = ArrayList<FrameLayout>()

    /** Finds the sponsored cards on screen and moves panels over them (adding/removing as needed). */
    fun update(root: AccessibilityNodeInfo) {
        val screen = Rect().also { root.getBoundsInScreen(it) }
        // Walk the tree: YouTube's feed is made of virtual views that findAccessibilityNodeInfosByText skips.
        val labels = ArrayList<AccessibilityNodeInfo>()
        collect(root, labels, 0)
        val cards = labels.mapNotNull { cardBounds(it, screen) }.distinct().take(MAX_PANELS)
        if (BuildConfig.DEBUG && (labels.isNotEmpty() || panels.isNotEmpty())) {
            android.util.Log.d("AdWizzAds", "sponsored labels=${labels.size} cards=$cards")
        }
        show(cards)
    }

    fun clear() = show(emptyList())

    val showing: Boolean get() = panels.isNotEmpty()

    private fun collect(n: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>, depth: Int) {
        if (depth > MAX_TREE_DEPTH || out.size >= MAX_PANELS) return
        if (n.isVisibleToUser && (AdRules.isSponsoredLabel(n.contentDescription?.toString()) || AdRules.isSponsoredLabel(n.text?.toString()))) {
            out += n
            return   // its children are part of the same card
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { collect(it, out, depth + 1) }
    }

    /** The list item holding the label: climb until the parent is the scrolling list. */
    private fun cardBounds(label: AccessibilityNodeInfo, screen: Rect): Rect? {
        var node: AccessibilityNodeInfo = label
        repeat(MAX_DEPTH) {
            val parent = node.parent ?: return null
            if (parent.isScrollable) {
                val r = Rect().also { node.getBoundsInScreen(it) }
                // Ignore anything that isn't card-sized, so we never cover the whole screen.
                return r.takeIf { it.height() in 1 until screen.height() * 9 / 10 && it.width() > screen.width() / 3 }
            }
            node = parent
        }
        return null
    }

    private fun show(cards: List<Rect>) {
        while (panels.size > cards.size) runCatching { wm.removeView(panels.removeAt(panels.lastIndex)) }
        cards.forEachIndexed { i, r ->
            val lp = params(r)
            if (i < panels.size) runCatching { wm.updateViewLayout(panels[i], lp) }
            else runCatching { val p = panel(); wm.addView(p, lp); panels += p }
                .onFailure { android.util.Log.w("AdWizzAds", "Couldn't cover a sponsored card", it) }
        }
    }

    private fun params(r: Rect) = WindowManager.LayoutParams(
        r.width(), r.height(), r.left, r.top,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun panel() = FrameLayout(context).apply {
        setBackgroundColor(Color.rgb(24, 24, 28))
        addView(TextView(context).apply {
            text = context.getString(R.string.sponsored_hidden)
            setTextColor(Color.rgb(150, 150, 160))
            textSize = 13f
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private companion object {
        const val MAX_PANELS = 4
        const val MAX_DEPTH = 12
        const val MAX_TREE_DEPTH = 40
    }
}
