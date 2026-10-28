package rocks.chirag.adwizz.focus

/**
 * Where short-form video lives in each app. Reels and Shorts come from the same servers as the
 * rest of Instagram / YouTube, so DNS can't block them: instead we spot their screens by view ID.
 * Apps rename views now and then; when a screen slips through, add its new ID here.
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
        "com.zhiliaoapp.musically",   // TikTok
        "com.ss.android.ugc.trill",   // TikTok (Asia)
        "in.mohalla.video",           // Moj
        "com.eterno.shortvideos",     // Josh
        "io.chingari.app",            // Chingari
    )

    /** Full view IDs ("pkg:id/name") to look for in [pkg], or empty if we don't watch it. */
    fun viewIdsFor(pkg: String): List<String> = SCREEN_IDS[pkg].orEmpty().map { "$pkg:id/$it" }
}
