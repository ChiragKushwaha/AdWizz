package rocks.chirag.adwizz.vpn

import android.content.Context
import android.util.Log
import rocks.chirag.adwizz.data.BlocklistRepository
import rocks.chirag.adwizz.data.UpdateNotifier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Hourly background job while protection is on: downloads missing / stale blocklists (then
 * [onListsChanged] hot-swaps the matcher) and checks GitHub for a newer AdWizz (at most daily).
 */
internal class Maintenance(
    private val context: Context,
    private val repo: BlocklistRepository,
    private val onListsChanged: () -> Unit,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        executor.scheduleWithFixedDelay(::run, 0, 1, TimeUnit.HOURS)
    }

    /** Downloads any list that's missing or stale, then reloads. Safe to call from any thread. */
    fun refreshLists() {
        try {
            if (repo.needsDownload()) {
                Log.i(TAG, repo.downloadAll())
                onListsChanged()
            }
        } catch (e: Exception) {
            Log.w(TAG, "List refresh failed", e)
        }
    }

    private fun run() {
        refreshLists()
        runCatching { UpdateNotifier.checkAndNotify(context) }.onFailure { Log.w(TAG, "Update check failed", it) }
    }

    override fun close() { executor.shutdownNow() }

    private companion object { const val TAG = "AdWizzVpn" }
}
