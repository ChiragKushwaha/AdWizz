package rocks.chirag.adwizz.focus

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Stands in for AdWizz's FocusStateProvider. */
class FakeSwitchProvider : ContentProvider() {
    companion object { @Volatile var value = 0 }
    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor =
        MatrixCursor(arrayOf("enabled")).apply { addRow(arrayOf(value)) }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
}

/** The add-on follows AdWizz's "Block Reels & Shorts" switch, and stays off without AdWizz. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FocusSwitchTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun installAdWizz() = Robolectric.buildContentProvider(FakeSwitchProvider::class.java)
        .create(ProviderInfo().apply { authority = FocusSwitch.URI.authority }).get()

    @Before fun reset() { FakeSwitchProvider.value = 0 }

    @Test fun pointsAtAdWizzsProvider() {
        assertEquals("content://${BuildConfig.MAIN_APP_ID}.focusstate/switch", FocusSwitch.URI.toString())
        assertEquals("rocks.chirag.adwizz.debug", BuildConfig.MAIN_APP_ID)   // debug pairs with debug
    }

    @Test fun offWithoutAdWizz() {
        val s = FocusSwitch(ctx).also { it.start() }
        assertFalse(s.on)
        s.stop()
    }

    @Test fun readsTheSwitch() {
        installAdWizz()
        FakeSwitchProvider.value = 1
        val s = FocusSwitch(ctx).also { it.start() }
        assertTrue(s.on)
        s.stop()
    }

    @Test fun followsChanges() {
        installAdWizz()
        val s = FocusSwitch(ctx).also { it.start() }
        assertFalse(s.on)
        FakeSwitchProvider.value = 1
        ctx.contentResolver.notifyChange(FocusSwitch.URI, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(s.on)
        s.stop()
    }

    @Test fun stopsListeningWhenStopped() {
        installAdWizz()
        val s = FocusSwitch(ctx).also { it.start(); it.stop() }
        FakeSwitchProvider.value = 1
        ctx.contentResolver.notifyChange(FocusSwitch.URI, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(s.on)
    }
}
