package rocks.chirag.adshield.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun prefs() = ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE)

    @Test fun defaults() {
        val s = Settings(ctx)
        assertFalse(s.wantEnabled)
        assertEquals("1.1.1.1", s.primaryDns)
        assertEquals("1.0.0.1", s.secondaryDns)
        assertTrue(s.catchHardcodedDns)
        assertTrue(s.blockTrackers)
        assertTrue(s.blockMalicious)
        assertEquals(Sources.DEFAULT_ENABLED, s.enabledSources)
        assertTrue(s.allowlist.isEmpty())
        assertTrue(s.customBlocklist.isEmpty())
        assertTrue(s.excludedApps.isEmpty())
        assertEquals("system", s.themeMode)
        assertEquals(0L, s.lastListUpdate)
        assertEquals(0L, s.lifetimeBlocked)
    }

    @Test fun valuesPersistAcrossInstances() {
        Settings(ctx).apply {
            wantEnabled = true; primaryDns = "9.9.9.9"; secondaryDns = ""
            catchHardcodedDns = false; blockTrackers = false; blockMalicious = false
            enabledSources = setOf("stevenblack"); allowlist = setOf("ok.com"); customBlocklist = setOf("bad.com")
            excludedApps = setOf("com.bank.app"); themeMode = "dark"; lastListUpdate = 42; lifetimeBlocked = 7
        }
        Settings(ctx).apply {
            assertTrue(wantEnabled)
            assertEquals("9.9.9.9", primaryDns)
            assertEquals("", secondaryDns)
            assertFalse(catchHardcodedDns); assertFalse(blockTrackers); assertFalse(blockMalicious)
            assertEquals(setOf("stevenblack"), enabledSources)
            assertEquals(setOf("ok.com"), allowlist)
            assertEquals(setOf("bad.com"), customBlocklist)
            assertEquals(setOf("com.bank.app"), excludedApps)
            assertEquals("dark", themeMode)
            assertEquals(42L, lastListUpdate)
            assertEquals(7L, lifetimeBlocked)
        }
    }

    @Test fun migrationAddsNewDefaultsAndDropsRemovedLists() {
        // An older install that saved its own source set.
        prefs().edit().putStringSet("sources", setOf("stevenblack", "hagezi_popup")).commit()
        val s = Settings(ctx)
        assertTrue(s.enabledSources.containsAll(Sources.DEFAULT_ENABLED))
        assertFalse("hagezi_popup" in s.enabledSources)
        assertEquals(Sources.REVISION, prefs().getInt("sources_rev", 0))
    }

    @Test fun migrationRunsOnlyOnce() {
        prefs().edit().putStringSet("sources", setOf("stevenblack")).commit()
        Settings(ctx) // migrates
        Settings(ctx).enabledSources = setOf("adguard_dns") // user turns things off afterwards
        assertEquals(setOf("adguard_dns"), Settings(ctx).enabledSources)
    }

    @Test fun freshInstallJustRecordsRevision() {
        Settings(ctx)
        assertFalse(prefs().contains("sources"))
        assertEquals(Sources.REVISION, prefs().getInt("sources_rev", 0))
    }

    @Test fun returnedSetsAreCopies() {
        val s = Settings(ctx)
        s.allowlist = setOf("a.com")
        val copy = s.allowlist.toMutableSet().apply { add("b.com") }
        assertEquals(setOf("a.com"), s.allowlist)
        assertEquals(2, copy.size)
    }
}
