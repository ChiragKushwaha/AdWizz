package rocks.chirag.adwizz.data

import org.json.JSONArray
import org.json.JSONObject

/** Counts for a range of days. Arrays are indexed by [BlockCategory] ordinal, then allowed last. */
data class StatsSummary(
    val totals: LongArray,
    /** Per app, sorted by most blocked first. */
    val perApp: List<Pair<String, LongArray>>,
    /** One entry per day in the range, oldest first (day as yyyymmdd). */
    val perDay: List<Pair<Int, LongArray>>,
    /** Most-blocked names with their counts. */
    val topDomains: List<Pair<String, Long>>,
) {
    fun total(c: BlockCategory) = totals[c.ordinal]
    val blocked: Long get() = totals.take(BlockCategory.entries.size).sum()
    val queries: Long get() = totals.sum()
}

/** The raw store behind [BlockStats]: day -> app -> counts, and day -> blocked name -> count. */
internal class StatsData {
    private val apps = HashMap<Int, HashMap<String, LongArray>>()
    private val domains = HashMap<Int, HashMap<String, Long>>()
    private val width = BlockCategory.entries.size + 1

    fun add(day: Int, app: String, domain: String, slot: Int) {
        apps.getOrPut(day) { HashMap() }.getOrPut(app) { LongArray(width) }[slot]++
        if (slot == BlockStats.ALLOWED) return
        val names = domains.getOrPut(day) { HashMap() }
        // Bounded: a day's list keeps its most frequent names, new rare ones are dropped.
        if (names.size < MAX_DOMAINS_PER_DAY || domain in names) names[domain] = (names[domain] ?: 0) + 1
    }

    fun summary(days: List<Int>): StatsSummary {
        val totals = LongArray(width)
        val perApp = HashMap<String, LongArray>()
        val perDay = days.map { day ->
            val dayTotals = LongArray(width)
            apps[day]?.forEach { (app, counts) ->
                val a = perApp.getOrPut(app) { LongArray(width) }
                for (i in counts.indices) { a[i] += counts[i]; dayTotals[i] += counts[i]; totals[i] += counts[i] }
            }
            day to dayTotals
        }
        val names = HashMap<String, Long>()
        days.forEach { d -> domains[d]?.forEach { (n, c) -> names[n] = (names[n] ?: 0) + c } }
        return StatsSummary(
            totals,
            perApp.entries.map { it.key to it.value }.sortedByDescending { it.second.take(width - 1).sum() },
            perDay,
            names.entries.sortedByDescending { it.value }.take(TOP_DOMAINS).map { it.key to it.value },
        )
    }

    /** Drops days before [firstKept]. */
    fun prune(firstKept: Int) {
        apps.keys.removeAll { it < firstKept }
        domains.keys.removeAll { it < firstKept }
    }

    fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("apps", JSONObject().also { o ->
            apps.forEach { (day, m) ->
                o.put(day.toString(), JSONObject().also { d -> m.forEach { (app, c) -> d.put(app, JSONArray(c.toList())) } })
            }
        })
        put("domains", JSONObject().also { o ->
            domains.forEach { (day, m) -> o.put(day.toString(), JSONObject(m as Map<*, *>)) }
        })
    }.toString()

    companion object {
        const val MAX_DOMAINS_PER_DAY = 400
        const val TOP_DOMAINS = 10

        fun fromJson(json: String): StatsData = StatsData().apply {
            val root = JSONObject(json)
            root.optJSONObject("apps")?.let { o ->
                o.keys().forEach { day ->
                    val d = o.getJSONObject(day)
                    val m = apps.getOrPut(day.toInt()) { HashMap() }
                    d.keys().forEach { app ->
                        val arr = d.getJSONArray(app)
                        // Older files may have fewer columns (a category added later): pad with zeros.
                        m[app] = LongArray(width) { i -> if (i < arr.length()) arr.getLong(i) else 0 }
                    }
                }
            }
            root.optJSONObject("domains")?.let { o ->
                o.keys().forEach { day ->
                    val d = o.getJSONObject(day)
                    domains[day.toInt()] = HashMap<String, Long>().apply { d.keys().forEach { put(it, d.getLong(it)) } }
                }
            }
        }
    }
}
