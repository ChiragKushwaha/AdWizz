package rocks.chirag.adwizz.focus

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Debug builds only: logs ad-related view IDs YouTube shows, so new IDs can be added to AdRules
 * when YouTube renames its player views. Release builds never call this.
 */
internal object DebugIds {
    private val seen = HashSet<String>()
    private val interesting = Regex("(^|[_/])(ad|ads|skip|sponsor|promo)([_]|$)", RegexOption.IGNORE_CASE)

    fun log(root: AccessibilityNodeInfo, result: String) {
        val found = sortedSetOf<String>()
        walk(root, found, 0)
        val fresh = found.filter { seen.add(it) }
        if (fresh.isNotEmpty() || result != "NO_AD") Log.d("AdWizzAds", "result=$result new ids=$fresh")
    }

    private fun walk(n: AccessibilityNodeInfo, out: MutableSet<String>, depth: Int) {
        if (depth > 40) return
        n.viewIdResourceName?.substringAfter(":id/")?.let { if (interesting.containsMatchIn(it)) out += it }
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, out, depth + 1) }
    }
}
