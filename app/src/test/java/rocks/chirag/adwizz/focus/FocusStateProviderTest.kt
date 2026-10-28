package rocks.chirag.adwizz.focus

import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import rocks.chirag.adwizz.data.Settings

/** The switch the add-on reads: one row, 1 or 0, and nobody can write it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FocusStateProviderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val provider = Robolectric.buildContentProvider(FocusStateProvider::class.java)
        .create(ProviderInfo().apply { authority = "${ctx.packageName}.focusstate" }).get()
    private val uri = FocusStateProvider.uri(ctx)

    private fun read(): Int = provider.query(uri, null, null, null, null).use { c ->
        assertEquals(1, c.count)
        assertEquals("enabled", c.getColumnName(0))
        c.moveToFirst(); c.getInt(0)
    }

    @Test fun reportsTheSwitch() {
        Settings(ctx).blockShortVideos = true
        assertEquals(1, read())
        Settings(ctx).blockShortVideos = false
        assertEquals(0, read())
    }

    @Test fun usesTheAppsOwnAuthority() {
        assertEquals("content://${ctx.packageName}.focusstate/switch", uri.toString())
    }

    @Test fun isReadOnly() {
        assertThrows(UnsupportedOperationException::class.java) { provider.insert(uri, ContentValues()) }
        assertThrows(UnsupportedOperationException::class.java) { provider.update(uri, ContentValues(), null, null) }
        assertThrows(UnsupportedOperationException::class.java) { provider.delete(uri, null, null) }
    }

    @Test fun tellsTheAddonWhenTheSwitchChanges() {
        FocusStateProvider.notifyChanged(ctx)
        assertTrue(uri in shadowOf(ctx.contentResolver).notifiedUris.map { it.uri })
    }
}
