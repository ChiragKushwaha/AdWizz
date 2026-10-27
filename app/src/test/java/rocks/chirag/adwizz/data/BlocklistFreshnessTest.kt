package rocks.chirag.adwizz.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** When lists are missing or stale and must be downloaded again. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BlocklistFreshnessTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var settings: Settings
    private lateinit var repo: BlocklistRepository
    private val listDir get() = File(ctx.filesDir, "lists")

    @Before fun setUp() {
        listDir.deleteRecursively()
        settings = Settings(ctx)
        repo = BlocklistRepository(ctx)
    }

    private fun writeList(id: String, body: String) = File(listDir.apply { mkdirs() }, "$id.txt").writeText(body)

    @Test fun needsDownloadWhenListMissing() {
        settings.lastListUpdate = System.currentTimeMillis()
        assertTrue(repo.needsDownload())
    }

    @Test fun needsDownloadWhenStale() {
        repo.activeSources().forEach { writeList(it.id, "0.0.0.0 x.test\n") }
        settings.lastListUpdate = System.currentTimeMillis() - 3L * 24 * 3600 * 1000
        assertTrue(repo.needsDownload())
    }

    @Test fun noDownloadWhenFreshAndComplete() {
        repo.activeSources().forEach { writeList(it.id, "0.0.0.0 x.test\n") }
        settings.lastListUpdate = System.currentTimeMillis()
        assertFalse(repo.needsDownload())
    }

    @Test fun emptyListFileCountsAsMissing() {
        repo.activeSources().forEach { writeList(it.id, "0.0.0.0 x.test\n") }
        writeList(repo.activeSources().first().id, "")
        settings.lastListUpdate = System.currentTimeMillis()
        assertTrue(repo.needsDownload())
    }
}
