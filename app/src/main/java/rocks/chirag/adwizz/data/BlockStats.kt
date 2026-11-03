package rocks.chirag.adwizz.data

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * Counts per day and per app: ads, trackers, threats and firewall blocks, plus allowed look-ups and
 * the most-blocked names. Kept in files/stats.json on the phone only, for [DAYS_KEPT] days.
 * Feeds the Stats tab. Thread-safe; the VPN records from many threads.
 */
object BlockStats {
    private const val TAG = "AdWizzStats"
    const val DAYS_KEPT = 30
    /** Index of the allowed count in a counts array; the rest follow [BlockCategory] order. */
    val ALLOWED = BlockCategory.entries.size
    private const val SAVE_EVERY_MS = 60_000L
    private const val REPEAT_MS = 3_000L

    /** Bumped whenever a count changes, so the Stats tab knows to refresh. */
    private val _version = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val version: kotlinx.coroutines.flow.StateFlow<Long> = _version

    private var file: File? = null
    private var data = StatsData()
    private var lastSave = 0L
    private var dirty = false
    /** Newest time recorded: old days are dropped relative to it, not the clock (which may jump). */
    private var newest = 0L
    /** Held for a whole save, so an explicit save waits for a background one to finish writing. */
    private val fileLock = Any()
    private val saver = Executors.newSingleThreadExecutor { r -> Thread(r, "adwizz-stats").apply { isDaemon = true } }
    /** Apps ask for A, AAAA and HTTPS records of a name at once: that's one attempt, not three. */
    private val recent = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 512
    }

    /** Points the store at its file and loads it. Cheap after the first call. */
    @Synchronized fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "stats.json")
        file = f
        data = runCatching { if (f.exists()) StatsData.fromJson(f.readText()) else StatsData() }
            .onFailure { Log.w(TAG, "Unreadable stats, starting over", it) }.getOrDefault(StatsData())
    }

    fun record(pkg: String?, domain: String, category: BlockCategory?, now: Long = System.currentTimeMillis()) {
        val app = pkg ?: AppNames.UNKNOWN
        synchronized(this) {
            val key = "$app|$domain|${category?.ordinal}"
            val last = recent[key]
            recent[key] = now
            if (last != null && now - last < REPEAT_MS) return
            data.add(dayOf(now), app, domain, category?.ordinal ?: ALLOWED)
            if (now > newest) newest = now
            dirty = true
            _version.value++
            if (now - lastSave < SAVE_EVERY_MS) return
            lastSave = now
        }
        saver.execute { save() }
    }

    /** Totals for the last [days] days (1 = today), for the dashboard. */
    @Synchronized fun summary(days: Int, now: Long = System.currentTimeMillis()): StatsSummary =
        data.summary(lastDays(days, now))

    /** Writes pending counts to disk (also drops days older than [DAYS_KEPT]). */
    fun save() {
        synchronized(fileLock) {
            val (f, json) = synchronized(this) {
                if (!dirty) return
                if (newest > 0) data.prune(lastDays(DAYS_KEPT, newest).first())
                dirty = false
                (file ?: return) to data.toJson()
            }
            runCatching {
                val tmp = File(f.path + ".tmp")
                tmp.writeText(json)
                tmp.renameTo(f)
            }.onFailure { Log.w(TAG, "Couldn't save stats", it) }
        }
    }

    /** Clears every count (the "Reset stats" button). */
    fun clear() {
        synchronized(this) { data = StatsData(); recent.clear(); dirty = true; _version.value++ }
        saver.execute { save() }
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTest() {
        synchronized(fileLock) {
            synchronized(this) { file = null; data = StatsData(); recent.clear(); lastSave = 0; dirty = false; newest = 0 }
        }
    }

    /** Day number as yyyymmdd in the phone's time zone, e.g. 20261103. */
    fun dayOf(time: Long): Int = Calendar.getInstance().run {
        timeInMillis = time
        get(Calendar.YEAR) * 10000 + (get(Calendar.MONTH) + 1) * 100 + get(Calendar.DAY_OF_MONTH)
    }

    /** The last [n] day numbers, oldest first, ending today. */
    fun lastDays(n: Int, now: Long): List<Int> = Calendar.getInstance().run {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, -(n - 1))
        List(n) { dayOf(timeInMillis).also { add(Calendar.DAY_OF_MONTH, 1) } }
    }
}
