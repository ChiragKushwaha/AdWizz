package rocks.chirag.adwizz.focus

/**
 * How the add-on spots video and audio ads that DNS can't block, because they come from the same
 * servers as the videos and songs. YouTube renames views now and then; when its ads slip through,
 * add the new IDs here. Every YouTube package must also be in res/xml/short_video_blocker.xml.
 */
object AdRules {
    val YOUTUBE_PACKAGES: Set<String> = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
    )

    /** The "Skip" button shown a few seconds into a skippable ad. */
    val SKIP_BUTTON_IDS: List<String> = listOf(
        "skip_ad_button",
        "skip_ad_button_text",
        "modern_skip_ad_text",
        "skip_ad_button_container",
        "ad_skip_button",
    )

    /** Views that only exist while an ad plays (the countdown, the "Ad" badge, "Visit advertiser"). */
    val AD_PLAYING_IDS: List<String> = listOf(
        "ad_progress_text",
        "ad_countdown",
        "ad_badge",
        "ad_text_badge",
        "player_learn_more_button",
        "ad_info_button",
        "visit_advertiser",
        "ad_cta_button",
    )

    /** Words on the skip button, used only while an ad is known to be playing. */
    val SKIP_WORDS: List<String> = listOf("Skip")

    /** Apps whose feeds mix in sponsored cards/posts, which the add-on covers up. */
    val SPONSORED_APPS: Set<String> = YOUTUBE_PACKAGES + "com.instagram.android"

    /** The label those apps put on a sponsored card, in the languages we've seen. */
    val SPONSORED_LABELS: List<String> = listOf(
        "Sponsored", "Gesponsert", "Patrocinado", "Sponsorisé", "Sponsorizzato", "Gesponsord",
        "Sponsorowane", "Реклама", "प्रायोजित", "স্পনসর করা", "مُموَّل", "Bersponsor", "Disponsori",
        "広告", "스폰서", "赞助内容", "Sponsorlu", "Được tài trợ",
    )

    /**
     * True for a sponsored label: "Sponsored", "Sponsored · Google", or a whole card described as
     * "Sponsored – <ad title>…" (YouTube). Not a video title that merely mentions the word.
     */
    fun isSponsoredLabel(text: String?): Boolean {
        val t = text?.trim() ?: return false
        return SPONSORED_LABELS.any { label -> t == label || SEPARATORS.any { t.startsWith("$label$it") } }
    }

    private val SEPARATORS = listOf(" ·", " •", " –", " -", " —", "\n")

    fun idsFor(pkg: String, names: List<String>): List<String> = names.map { "$pkg:id/$it" }

    const val SPOTIFY_PACKAGE = "com.spotify.music"
    const val SPOTIFY_METADATA = "com.spotify.music.metadatachanged"
    const val SPOTIFY_PLAYBACK = "com.spotify.music.playbackstatechanged"

    /** Spotify names ads "spotify:ad:…" when it reports them at all. */
    fun isSpotifyAdId(id: String?): Boolean = id != null && id.startsWith("spotify:ad:")

    /** Spotify reports length in seconds in older versions and milliseconds in newer ones. */
    fun spotifyLengthMs(length: Long): Long = if (length in 1..20_000) length * 1000 else length
}
