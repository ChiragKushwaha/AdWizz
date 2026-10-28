package rocks.chirag.adwizz.data

import android.util.Log
import rocks.chirag.adwizz.core.DomainIndex
import rocks.chirag.adwizz.core.ListParser
import java.io.File
import java.io.Reader

/**
 * Keeps the on-device blocklist database ([DomainIndex]) in step with the downloaded lists.
 * It's rebuilt only when something it was built from changes (a list was re-downloaded, or a
 * list was switched on/off); otherwise the saved file is opened as-is, which is instant.
 */
internal class BlocklistIndex(private val dir: File) {
    private val file = File(dir, "blocked.idx")
    private val keyFile = File(dir, "blocked.key")
    private var current: Pair<String, DomainIndex>? = null

    /** One input to the index: a name plus how to read it. */
    class Input(val id: String, val version: String, val open: () -> Reader)

    /** Number of times the index was rebuilt (diagnostics and tests). */
    @Volatile var builds = 0
        private set

    @Synchronized
    fun get(inputs: List<Input>): DomainIndex {
        val key = "v$FORMAT|" + inputs.joinToString("|") { "${it.id}:${it.version}" }
        current?.let { (k, idx) -> if (k == key) return idx }
        if (keyFile.exists() && runCatching { keyFile.readText() }.getOrNull() == key) {
            runCatching { DomainIndex.open(file) }.getOrNull()?.let { current = key to it; return it }
        }
        return build(inputs, key)
    }

    private fun build(inputs: List<Input>, key: String): DomainIndex {
        val start = System.currentTimeMillis()
        // Roughly one domain per 16 bytes of list text: sized up front so millions of domains don't regrow the array.
        val estimate = inputs.sumOf { it.version.substringBefore('@').toLongOrNull() ?: 0L } / 16
        val builder = DomainIndex.Builder(estimate.coerceIn(10_000L, 8_000_000L).toInt())
        for (input in inputs) {
            runCatching { input.open().use { ListParser.parseInto(it, builder::add) } }
                .onFailure { Log.w(TAG, "Failed to read ${input.id}", it) }
        }
        dir.mkdirs()
        keyFile.delete()   // never pair a new index with an old key if we crash mid-write
        val n = builder.writeTo(file)
        keyFile.writeText(key)
        builds++
        Log.i(TAG, "Built blocklist database: $n domains in ${System.currentTimeMillis() - start} ms")
        return DomainIndex.open(file).also { current = key to it }
    }

    companion object {
        private const val TAG = "Blocklist"
        /** Bump when the index format or parsing rules change, so old databases are rebuilt. */
        const val FORMAT = 1

        /** A downloaded list's version: its size and when it was last downloaded. */
        fun version(f: File) = "${f.length()}@${f.lastModified()}"
    }
}
