package rocks.chirag.adwizz.data

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import rocks.chirag.adwizz.core.DomainMatcher
import rocks.chirag.adwizz.core.DomainSet
import rocks.chirag.adwizz.core.ListParser

/**
 * Live check that the default lists (downloaded now) block ads without breaking popular apps.
 * Skipped unless run with: ./gradlew testDebugUnitTest -Plive=true --tests '*PopularAppsLiveTest'
 */
class PopularAppsLiveTest {
    companion object {
        private var matcher: DomainMatcher? = null

        private fun live() = System.getProperty("adwizz.live") == "true"

        private fun fetch(url: String): String {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 120_000
            c.setRequestProperty("User-Agent", "AdWizz")
            return try { c.inputStream.readBytes().decodeToString() } finally { c.disconnect() }
        }

        /** Default switches: the default ad lists plus trackers, security and DNS-bypass lists. */
        @BeforeClass @JvmStatic fun load() {
            if (!live()) return
            val set = DomainSet(1_000_000)
            Sources.ALL.filter { it.id in Sources.DEFAULT_ENABLED || it.group != SourceGroup.ADS }.forEach { src ->
                val text = src.urls.firstNotNullOfOrNull { runCatching { fetch(it) }.getOrNull() } ?: error("Couldn't download ${src.id}")
                ListParser.parseInto(text.reader(), set)
            }
            matcher = DomainMatcher(set, DomainSet())
        }
    }

    private fun m(): DomainMatcher {
        assumeTrue("live tests off (pass -Plive=true)", live())
        return matcher!!
    }

    /** Hosts these apps can't work without: blocking any of them breaks the app, not just its ads. */
    private val essential = listOf(
        // YouTube: site, API, thumbnails and the video servers (which also carry its ads)
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtubei.googleapis.com",
        "i.ytimg.com", "yt3.ggpht.com", "rr1---sn-a5mekn6d.googlevideo.com", "redirector.googlevideo.com",
        // Instagram / Facebook / Messenger / WhatsApp
        "instagram.com", "i.instagram.com", "graph.instagram.com", "scontent.cdninstagram.com",
        "graph.facebook.com", "edge-mqtt.facebook.com", "mqtt-mini.facebook.com", "web.whatsapp.com",
        "g.whatsapp.net", "mmg.whatsapp.net",
        // Google services
        "android.clients.google.com", "play.googleapis.com", "mail.google.com", "maps.googleapis.com",
        "www.google.com", "accounts.google.com", "fonts.gstatic.com",
        // Streaming, social, shopping, payments, food (popular in India and worldwide)
        "www.netflix.com", "api-global.netflix.com", "open.spotify.com", "apresolve.spotify.com",
        "x.com", "api.x.com", "www.reddit.com", "www.amazon.in", "www.flipkart.com",
        "www.hotstar.com", "api.phonepe.com", "www.paytm.com", "www.swiggy.com", "www.zomato.com",
        "telegram.org", "discord.com", "www.linkedin.com",
    )

    @Test fun popularAppsKeepWorking() {
        val blocked = essential.filter { m().isBlocked(it) }
        assertTrue("These essential hosts would be blocked: $blocked", blocked.isEmpty())
    }

    @Test fun commonAdAndTrackerHostsAreBlocked() {
        val ads = listOf(
            "doubleclick.net", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com", "ads.youtube.com",
            "app-measurement.com", "graph.facebook.net.example.invalid".takeIf { false },
            "an.facebook.com", "ads.tiktok.com", "applovin.com", "unityads.unity3d.com", "ads.mopub.com",
            "adcolony.com", "inmobi.com", "outcome-ssp.supersonicads.com", "vungle.com", "chartboost.com", "admob.com",
            "click.linksynergy.com", "t.co.example.invalid".takeIf { false }, "bit.ly.example.invalid".takeIf { false },
        ).filterNotNull()
        val missed = ads.filterNot { m().isBlocked(it) }
        assertTrue("Not blocked: $missed", missed.isEmpty())
    }

    @Test fun listIsBigButFitsOnAPhone() {
        val n = m().size()
        assertTrue("only $n domains", n > 800_000)
        assertTrue("$n domains is too many for low-memory phones", n < 2_000_000)
    }
}
