package rocks.chirag.adshield.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * canyoublockit.com is a public ad-blocker test site full of real ad-network scripts
 * (banners, pop-unders, interstitials, push ads). These tests make sure AdWizz blocks all of it.
 *
 * Note: some "ads" on that site are the site's own example screenshots (files on canyoublockit.com
 * such as "Scum Interstitial Ad Placement") and a self-hosted video. Those are page content, not
 * ads, and no blocker removes them, so the site itself must stay reachable.
 */
object CanYouBlockIt {
    /** Ad / tracking hosts the site's pages loaded when checked on 2026-10-27 (page + runtime). */
    val AD_HOSTS = listOf(
        "12ezo5v60.com",                 // pop-under / direct-link loader
        "fvcwqkkqmuv.com",               // interstitial loader
        "ybs2ffs7v.com",                 // banner ad loader (4 slots)
        "www.kjfhenoqfyfljo.com",        // second-stage ad server
        "d3cod80thn7qnd.cloudfront.net", // ad creatives on CloudFront
        "www.antiadblocksystems.com",    // anti-adblock recovery script
        "pagead2.googlesyndication.com", // Google ads
        "stats.g.doubleclick.net",
        "www.googletagmanager.com",
        "www.pgjlctmswgnwf.com",         // brand-new, on no list: caught by gibberish-domain detection
    )

    /** Must keep working or the test site (and real sites like it) would break. */
    val SITE_HOSTS = listOf("canyoublockit.com", "fonts.googleapis.com", "fonts.gstatic.com", "player.vimeo.com")

    val PAGES = listOf("https://canyoublockit.com/", "https://canyoublockit.com/extreme-test/")
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CanYouBlockItTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /** Offline: with no lists downloaded yet (first launch), the bundled starter list must cover the site. */
    @Test fun starterListBlocksEveryKnownAdHost() {
        File(ctx.filesDir, "lists").deleteRecursively()
        val m = BlocklistRepository(ctx).buildMatcher()
        val missed = CanYouBlockIt.AD_HOSTS.filterNot { m.isBlocked(it) }
        assertTrue("Ad hosts not blocked: $missed", missed.isEmpty())
    }

    @Test fun siteItselfIsNotBlocked() {
        val m = BlocklistRepository(ctx).buildMatcher()
        CanYouBlockIt.SITE_HOSTS.forEach { assertFalse(it, m.isBlocked(it)) }
    }

    @Test fun allowlistingAnAdHostIsRespected() {
        Settings(ctx).allowlist = setOf("ybs2ffs7v.com")
        val m = BlocklistRepository(ctx).buildMatcher()
        assertFalse(m.isBlocked("ybs2ffs7v.com"))
        assertTrue(m.isBlocked("fvcwqkkqmuv.com"))
    }
}
