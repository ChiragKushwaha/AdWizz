package rocks.chirag.adwizz.data

/**
 * "Block sponsored content": the networks behind "Sponsored stories", "Promoted links" and
 * "Around the web" boxes on news sites and in apps. They serve only that kind of content, so
 * blocking them removes the boxes without breaking the page around them.
 */
object SponsoredRules {
    val DOMAINS: List<String> = listOf(
        "taboola.com", "taboolasyndication.com",       // Taboola
        "outbrain.com", "outbrainimg.com",              // Outbrain
        "mgid.com", "revcontent.com", "content.ad", "zergnet.com",
        "nativo.com", "ntv.io", "postrelease.com",      // Nativo
        "sharethrough.com", "triplelift.com", "3lift.com",
        "adblade.com", "dianomi.com", "plista.com", "ligatus.com",
        "engageya.com", "lockerdome.com", "popin.cc", "yieldmo.com", "adnow.com",
        "bidtellect.com", "polarmobile.com", "adyoulike.com", "speakol.com",
        "gemini.yahoo.com",                             // Yahoo's sponsored posts
    )
}
