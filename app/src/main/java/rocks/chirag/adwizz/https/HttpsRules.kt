package rocks.chirag.adwizz.https

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * What HTTPS filtering does with a decrypted request: block ad URLs that share a host with real
 * content (YouTube's ad pings), and rewrite YouTube's player data and web pages so ads and
 * sponsored boxes don't show. Plain byte work, no Android: unit-tested on the JVM.
 */
internal object HttpsRules {
    private val YOUTUBE_HOSTS = setOf("www.youtube.com", "m.youtube.com", "youtube.com", "music.youtube.com")

    /** Ad and ad-measurement paths on hosts that also serve the videos. */
    private val YOUTUBE_AD_PATHS = listOf(
        "/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_info", "/pcs/activeview",
        "/youtubei/v1/player/ad_break", "/generate_204?ad", "/api/stats/qoe?adformat",
    )

    /** YouTube API calls and pages whose JSON carries ad slots. */
    private val YOUTUBE_DATA_PATHS = listOf(
        "/youtubei/v1/player", "/youtubei/v1/next", "/youtubei/v1/browse", "/youtubei/v1/search",
        "/youtubei/v1/reel", "/watch", "/results", "/shorts", "/@", "/channel", "/feed",
    )

    /**
     * Keys YouTube's player and feed read ads from. Renaming a key keeps the JSON valid, and the
     * player simply doesn't find any ads; the feed skips cards it doesn't recognise.
     */
    private val YOUTUBE_AD_KEYS = listOf(
        "adPlacements", "playerAds", "adSlots", "adSlotRenderer", "promotedSparklesWebRenderer",
        "searchPyvRenderer", "displayAdRenderer", "promotedVideoRenderer", "compactPromotedVideoRenderer",
        "inFeedAdLayoutRenderer", "playerLegacyDesktopWatchAdsRenderer", "adBreakServiceRenderer",
        "bannerPromoRenderer", "statementBannerRenderer", "brandVideoShelfRenderer", "mealbarPromoRenderer",
    )

    /** Hides ad slots and sponsored boxes left on pages (their scripts are blocked, the empty box isn't). */
    const val HIDE_CSS = "ins.adsbygoogle,.adsbygoogle,[id^=\"div-gpt-ad\"],[id^=\"google_ads_iframe\"]," +
        "iframe[src*=\"doubleclick.net\"],iframe[src*=\"googlesyndication.com\"],[data-ad-slot],[data-google-query-id]," +
        ".trc_related_container,.trc_rbox_container,[id^=\"taboola-\"],.OUTBRAIN,.ob-widget,[data-widget-id^=\"AR_\"]," +
        "ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,ytd-promoted-sparkles-web-renderer,ytd-display-ad-renderer," +
        "ytd-banner-promo-renderer,ytd-statement-banner-renderer,ytd-player-legacy-desktop-watch-ads-renderer," +
        "ytm-promoted-sparkles-web-renderer,ad-slot-renderer,ytm-companion-ad-renderer,#player-ads,#masthead-ad" +
        "{display:none!important}"

    private val STYLE_TAG = "<style id=\"adwizz\">$HIDE_CSS</style>".toByteArray()

    fun isYoutube(host: String) = host in YOUTUBE_HOSTS

    /** True if this URL is an ad (blocked with an empty answer). */
    fun isBlockedUrl(host: String, path: String): Boolean =
        isYoutube(host) && YOUTUBE_AD_PATHS.any { path.startsWith(it) }

    /** True if the response is worth buffering and rewriting: YouTube data, or a web page. */
    fun wantsRewrite(host: String, path: String, accept: String?, fetchDest: String?): Boolean =
        (isYoutube(host) && YOUTUBE_DATA_PATHS.any { path.startsWith(it) }) ||
            fetchDest == "document" || fetchDest == "iframe" || accept?.startsWith("text/html") == true

    /** True for content types we know how to rewrite safely. */
    fun canRewrite(contentType: String?) = contentType != null &&
        (contentType.startsWith("text/html") || contentType.contains("json") || contentType.startsWith("text/plain"))

    /** The rewritten body (already decompressed). */
    fun rewrite(host: String, contentType: String?, body: ByteArray): ByteArray {
        var b = body
        if (isYoutube(host)) for (key in YOUTUBE_AD_KEYS) b = replace(b, "\"$key\"", "\"${key}_adwizz\"")
        if (contentType?.startsWith("text/html") == true) b = injectStyle(b)
        return b
    }

    /** Inserts our style sheet before </head> (or at the start if there's no head). */
    fun injectStyle(html: ByteArray): ByteArray {
        val at = indexOfIgnoreCase(html, "</head>".toByteArray())
        if (at < 0) return STYLE_TAG + html
        return html.copyOfRange(0, at) + STYLE_TAG + html.copyOfRange(at, html.size)
    }

    fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(b.inputStream()).use { it.readBytes() }

    /** Replaces every occurrence of [from] with [to] (ASCII). */
    fun replace(src: ByteArray, from: String, to: String): ByteArray {
        val f = from.toByteArray(); val t = to.toByteArray()
        var i = indexOf(src, f, 0)
        if (i < 0) return src
        val out = ByteArrayOutputStream(src.size + 64)
        var start = 0
        while (i >= 0) {
            out.write(src, start, i - start); out.write(t)
            start = i + f.size
            i = indexOf(src, f, start)
        }
        out.write(src, start, src.size - start)
        return out.toByteArray()
    }

    private fun indexOf(src: ByteArray, p: ByteArray, from: Int): Int {
        outer@ for (i in from..src.size - p.size) {
            for (j in p.indices) if (src[i + j] != p[j]) continue@outer
            return i
        }
        return -1
    }

    private fun indexOfIgnoreCase(src: ByteArray, p: ByteArray): Int {
        outer@ for (i in 0..src.size - p.size) {
            for (j in p.indices) if (Character.toLowerCase(src[i + j].toInt()) != p[j].toInt()) continue@outer
            return i
        }
        return -1
    }
}
