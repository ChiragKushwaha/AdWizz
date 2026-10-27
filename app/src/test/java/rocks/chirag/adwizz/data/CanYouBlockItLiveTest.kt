package rocks.chirag.adwizz.data

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import rocks.chirag.adwizz.core.DomainMatcher
import rocks.chirag.adwizz.core.DomainSet
import rocks.chirag.adwizz.core.ListParser

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
            ListParser.parseInto(text.reader(), set)
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
