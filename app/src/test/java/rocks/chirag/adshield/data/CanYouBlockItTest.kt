package rocks.chirag.adshield.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import rocks.chirag.adshield.core.DomainMatcher
import rocks.chirag.adshield.core.DomainSet
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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

/**
 * Live check against the real site and the real downloaded lists.
 * Skipped unless run with: ./gradlew testDebugUnitTest -Plive=true --tests '*CanYouBlockItLiveTest'
 */
class CanYouBlockItLiveTest {
    private fun live() = assumeTrue("live tests off (pass -Plive=true)", System.getProperty("adwizz.live") == "true")

    private fun fetch(url: String, referer: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        referer?.let { c.setRequestProperty("Referer", it) }
        return try { c.inputStream.readBytes().decodeToString() } finally { c.disconnect() }
    }

    /** Builds the matcher exactly like the app does with the default switches. */
    private fun liveMatcher(): DomainMatcher {
        val set = DomainSet(1_000_000)
        val active = Sources.ALL.filter {
            it.id in Sources.DEFAULT_ENABLED || it.group != SourceGroup.ADS
        }
        for (src in active) {
            val text = src.urls.firstNotNullOfOrNull { u -> runCatching { fetch(u) }.getOrNull() }
                ?: error("Couldn't download ${src.id}")
            DomainMatcher.parseInto(text.reader(), set)
        }
        return DomainMatcher(set, DomainSet()).withGeneratedDomainBlocking(true)
    }

    private fun thirdPartyScriptHosts(html: String): Set<String> =
        Regex("""<(?:script|iframe)[^>]+src=["']?(?:https?:)?//([a-z0-9.-]+)""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.groupValues[1].lowercase() }
            .filterNot { h -> CanYouBlockIt.SITE_HOSTS.any { h == it || h.endsWith(".$it") } }
            .toSet()

    @Test fun everyThirdPartyScriptOnTheLiveSiteIsBlocked() {
        live()
        val m = liveMatcher()
        val hosts = CanYouBlockIt.PAGES.flatMap { thirdPartyScriptHosts(fetch(it)) }.toSet()
        assertTrue("found no third-party scripts — did the site change?", hosts.isNotEmpty())
        // Harmless libraries the site uses that aren't ads (video player, cookie banner).
        val notAds = setOf("cdn.fluidplayer.com", "cdn.iubenda.com", "www.iubenda.com", "player.vimeo.com")
        val missed = hosts.filterNot { it in notAds || m.isBlocked(it) }
        assertTrue("Live ad hosts NOT blocked: $missed (of $hosts)", missed.isEmpty())
    }

    @Test fun knownAdHostsAreOnTheLiveLists() {
        live()
        val m = liveMatcher()
        val missed = CanYouBlockIt.AD_HOSTS.filterNot { m.isBlocked(it) }
        assertTrue("Not on live lists: $missed", missed.isEmpty())
        CanYouBlockIt.SITE_HOSTS.forEach { assertFalse(it, m.isBlocked(it)) }
    }
}
