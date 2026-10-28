package rocks.chirag.adwizz.focus

/**
 * Where short-form video lives. Reels and Shorts come from the same servers as the rest of
 * Instagram / YouTube, so DNS can't block them: in apps we spot their screens by view ID, and in
 * browsers we check the address bar. Apps rename views now and then; when a screen slips through,
 * add its new ID here. Every package here must also be in res/xml/short_video_blocker.xml.
 */
object ShortVideoRules {
    /** Package -> view IDs that only exist on that app's short-video player. */
    val SCREEN_IDS: Map<String, List<String>> = mapOf(
        "com.google.android.youtube" to listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_fragment_root",
        ),
        "com.instagram.android" to listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "root_clips_layout",
        ),
    )

    /** Apps that are nothing but short videos: these get sent back to the home screen. */
    val WHOLE_APPS: Set<String> = setOf(
        "com.zhiliaoapp.musically",       // TikTok
        "com.ss.android.ugc.trill",       // TikTok (Asia)
        "com.zhiliaoapp.musically.go",    // TikTok Lite
        "in.mohalla.video",               // Moj
        "com.eterno.shortvideos",         // Josh
        "io.chingari.app",                // Chingari
        "video.like",                     // Likee
        "com.kwai.video",                 // Kwai
        "com.kwai.bulldog",               // Snack Video
        "com.next.innovation.takatak",    // MX TakaTak
        "co.triller.droid",               // Triller
        "com.roposo.android",             // Roposo
    )

    /** Browser package -> view IDs of its address bar. */
    val BROWSER_URL_IDS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.chrome.dev" to listOf("url_bar"),
        "com.chrome.canary" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_view"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view", "display_url"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
    )

    /** Sites that only serve short videos: blocked on any page. */
    private val SHORT_VIDEO_SITES = listOf(
        "tiktok.com", "likee.video", "kwai.com", "snackvideo.com", "triller.co", "mxtakatak.com",
        "roposo.com", "mojapp.in", "chingari.io",
    )

    /** "site/path-prefix" pages that are a short-video feed on otherwise normal sites. */
    private val SHORT_VIDEO_PAGES = listOf(
        "youtube.com/shorts",
        "instagram.com/reel",          // also matches /reels
        "facebook.com/reel",           // also matches /reels
        "snapchat.com/spotlight",
    )

    /**
     * Domains blocked over DNS while the switch is on (needs the VPN). These belong only to
     * short-video services, so blocking them also stops their videos inside other apps.
     */
    val DOMAINS: List<String> = listOf(
        "tiktok.com", "tiktokv.com", "tiktokv.us", "tiktok.us", "tiktokcdn.com", "tiktokcdn-us.com",
        "tiktokw.us", "ttwstatic.com", "musical.ly", "muscdn.com",
        "likee.video", "like.video", "likeevideo.com",
        "kwai.com", "kwai.net", "kwaicdn.com", "snackvideo.com",
        "triller.co", "mxtakatak.com", "roposo.com", "mojapp.in", "chingari.io",
    )

    /** Full view IDs ("pkg:id/name") to look for in [pkg], or empty if we don't watch it. */
    fun viewIdsFor(pkg: String): List<String> = SCREEN_IDS[pkg].orEmpty().map { "$pkg:id/$it" }

    /** Full view IDs of [pkg]'s address bar, or empty if it isn't a browser we know. */
    fun urlBarIdsFor(pkg: String): List<String> = BROWSER_URL_IDS[pkg].orEmpty().map { "$pkg:id/$it" }

    /** True if [address] (as a browser's address bar shows it) is a short-video page. */
    fun isShortVideoUrl(address: String): Boolean {
        var a = address.trim().lowercase()
        if (a.isEmpty() || ' ' in a) return false   // a search, not an address
        a = a.substringAfter("://")
        val host = a.substringBefore('/').substringBefore('?').substringBefore(':')
        val path = a.substring(host.length)
        val site = SHORT_VIDEO_SITES.firstOrNull { host == it || host.endsWith(".$it") }
        if (site != null) return true
        return SHORT_VIDEO_PAGES.any { page ->
            val pageHost = page.substringBefore('/')
            val pagePath = page.substring(pageHost.length)
            (host == pageHost || host.endsWith(".$pageHost")) && path.startsWith(pagePath)
        }
    }
}
